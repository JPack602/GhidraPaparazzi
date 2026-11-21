package com.xebyte.core;

import ghidra.app.plugin.core.decompile.DecompilerProvider;
import ghidra.app.services.ProgramManager;
import ghidra.framework.model.Project;
import ghidra.framework.model.ToolManager;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** GUI-only tools; register this service ONLY in GhidraMCPPlugin.buildScanner(). */
public final class DecompilerCaptureService {
    private final PluginTool frontEnd;
    private final GuiToolService navigation;

    public DecompilerCaptureService(PluginTool frontEnd, GuiToolService navigation) {
        this.frontEnd = frontEnd;
        this.navigation = navigation;
    }

    @McpTool(path = "/capture_decompiler_png", method = "POST", dryRun = false,
        category = "utility", access = ToolAccess.WRITE,
        description = "Save the visible Ghidra Decompiler pane as PNG (not the entire desktop). " +
            "Navigate first with tool_goto_address. output_path is restricted to the capture directory.")
    public synchronized Response captureDecompiler(
        @Param(value = "output_path", source = ParamSource.BODY,
            description = "PNG filename or absolute path within GHIDRA_MCP_CAPTURE_DIR") String outputPath,
        @Param(value = "expected_address", source = ParamSource.BODY, defaultValue = "",
            description = "Optional address; wait until its function is actually displayed") String expectedAddress,
        @Param(value = "timeout_ms", source = ParamSource.BODY, defaultValue = "10000",
            description = "Wait for Decompiler to finish, 1..20000 ms") int timeoutMs) {
        if (expectedAddress == null || expectedAddress.isBlank())
            return capture(outputPath, null, null, timeoutMs, false);
        PluginTool browser = codeBrowser();
        if (browser == null) return Response.err("Open a CodeBrowser and show the Decompiler pane");
        Program program = browser.getService(ProgramManager.class).getCurrentProgram();
        if (program == null) return Response.err("No program open in CodeBrowser");
        Address target = ServiceUtils.parseAddress(program, expectedAddress);
        if (target == null) return Response.err("Invalid expected_address: " + expectedAddress);
        Function function = program.getFunctionManager().getFunctionContaining(target);
        if (function == null) return Response.err("No function at expected_address");
        return capture(outputPath, function.getEntryPoint(), null, timeoutMs, false);
    }

    @McpTool(path = "/capture_evidence", method = "POST", dryRun = false,
        category = "utility", access = ToolAccess.WRITE,
        description = "Navigate to an address, wait for the matching function to decompile, " +
            "and save its Decompiler pane with a labelled evidence header and red border.")
    public synchronized Response captureEvidence(
        @Param(value = "address", source = ParamSource.BODY, paramType = Param.ADDRESS,
            description = "Function address, e.g. 0x00401234") String address,
        @Param(value = "output_path", source = ParamSource.BODY,
            description = "PNG filename or absolute path within GHIDRA_MCP_CAPTURE_DIR") String outputPath,
        @Param(value = "label", source = ParamSource.BODY, defaultValue = "Evidence",
            description = "Caption added above the captured Decompiler pane") String label,
        @Param(value = "timeout_ms", source = ParamSource.BODY, defaultValue = "10000",
            description = "Wait for Decompiler to finish, 1..20000 ms") int timeoutMs) {
        if (address == null || address.isBlank()) return Response.err("address is required");
        if (SwingUtilities.isEventDispatchThread()) return Response.err("Call from an HTTP worker, not Swing EDT");
        try {
            PluginTool browser = codeBrowser();
            if (browser == null) return Response.err("Open a CodeBrowser and show the Decompiler pane");
            ProgramManager pm = browser.getService(ProgramManager.class);
            Program program = pm.getCurrentProgram();
            if (program == null) return Response.err("No program open in CodeBrowser");
            Address target = ServiceUtils.parseAddress(program, address);
            if (target == null) return Response.err("Invalid address: " + address);
            Function f = program.getFunctionManager().getFunctionContaining(target);
            if (f == null) return Response.err("No function contains address " + address);
            Response result = navigation.gotoAddress(address);
            if (result instanceof Response.Err) return result;
            return capture(outputPath, f.getEntryPoint(), label, timeoutMs, true);
        } catch (Exception e) {
            return Response.err("Evidence capture failed: " + e.getMessage());
        }
    }

    private PluginTool codeBrowser() {
        Project project = frontEnd.getProject();
        if (project == null) return null;
        ToolManager tm = project.getToolManager();
        if (tm == null) return null;
        for (PluginTool t : tm.getRunningTools()) {
            if (t.getService(ProgramManager.class) != null) return t;
        }
        return null;
    }

    private record Shot(BufferedImage image, String program, String function,
                        String entry, boolean ready, String reason) { }

    private Shot observe(PluginTool browser, Address expectedEntry, boolean paint) throws Exception {
        AtomicReference<Shot> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Object cp = browser.getComponentProvider("Decompiler");
            if (!(cp instanceof DecompilerProvider provider)) {
                ref.set(new Shot(null, "", "", "", false, "Decompiler pane missing"));
                return;
            }
            JComponent component = provider.getComponent();
            Function f = provider.getController().getFunction();
            boolean complete = !provider.getController().isDecompiling() &&
                provider.getController().hasDecompileResults();
            boolean matches = f != null && (expectedEntry == null ||
                f.getEntryPoint().equals(expectedEntry));
            if (!complete || !matches || !component.isShowing() ||
                    component.getWidth() <= 0 || component.getHeight() <= 0) {
                ref.set(new Shot(null, "", "", "", false,
                    "waiting for visible Decompiler and matching completed function"));
                return;
            }
            BufferedImage image = null;
            if (paint) {
                image = new BufferedImage(component.getWidth(), component.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try { component.printAll(graphics); }
                finally { graphics.dispose(); }
            }
            ref.set(new Shot(image, f.getProgram().getName(), f.getName(),
                f.getEntryPoint().toString(), true, ""));
        });
        return ref.get();
    }

    private synchronized Response capture(String outputPath, Address expectedEntry,
                                           String label, int timeoutMs, boolean evidence) {
        if (SwingUtilities.isEventDispatchThread()) return Response.err("Call from an HTTP worker, not Swing EDT");
        if (timeoutMs < 1 || timeoutMs > 20000) return Response.err("timeout_ms must be 1..20000");
        try {
            Path destination = secureDestination(outputPath);
            PluginTool browser = codeBrowser();
            if (browser == null) return Response.err("Open a CodeBrowser with the Decompiler pane enabled");
            long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            Shot shot;
            do {
                shot = observe(browser, expectedEntry, false);
                if (shot.ready()) break;
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            if (!shot.ready()) return Response.err("Timed out: " + shot.reason());
            shot = observe(browser, expectedEntry, true);
            if (!shot.ready()) return Response.err("Decompiler changed during capture; retry");
            BufferedImage image = evidence ? decorate(shot.image(), label, shot) : shot.image();
            // CREATE_NEW prevents overwriting an existing file (including symlinks).
            try (var stream = Files.newOutputStream(destination,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                if (!ImageIO.write(image, "png", stream)) throw new IllegalStateException("No PNG writer");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("saved", true);
            out.put("path", destination.toString());
            out.put("bytes", Files.size(destination));
            out.put("program", shot.program());
            out.put("function", shot.function());
            out.put("entry", shot.entry());
            out.put("width", image.getWidth());
            out.put("height", image.getHeight());
            return Response.ok(out);
        } catch (FileAlreadyExistsException e) {
            return Response.err("File exists; choose a new output_path: " + e.getFile());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.err("Capture interrupted");
        } catch (Exception e) {
            return Response.err("Capture failed: " + e.getMessage());
        }
    }

    private static Path secureDestination(String outputPath) throws Exception {
        if (outputPath == null || outputPath.isBlank()) throw new IllegalArgumentException("output_path is required");
        if (!outputPath.toLowerCase(Locale.ROOT).endsWith(".png"))
            throw new IllegalArgumentException("output_path must end with .png");
        String configured = System.getenv("GHIDRA_MCP_CAPTURE_DIR");
        Path root = Paths.get(configured == null || configured.isBlank()
            ? System.getProperty("user.home") + "/GhidraMCP-Captures" : configured);
        Files.createDirectories(root);
        root = root.toRealPath();
        Path requested = Paths.get(outputPath);
        Path target = (requested.isAbsolute() ? requested : root.resolve(requested)).normalize();
        if (!target.startsWith(root) || target.equals(root))
            throw new SecurityException("output_path must stay inside " + root);
        Path parent = target.getParent();
        Files.createDirectories(parent);
        if (!parent.toRealPath().startsWith(root))
            throw new SecurityException("output_path parent resolves outside " + root);
        return target;
    }

    private static BufferedImage decorate(BufferedImage source, String label, Shot shot) {
        int header = 68;
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight() + header,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(32, 37, 48));
            g.fillRect(0, 0, image.getWidth(), header);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            String heading = label == null || label.isBlank() ? "Evidence" : label;
            g.drawString(heading.substring(0, Math.min(heading.length(), 80)), 15, 28);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            String info = shot.program() + " | " + shot.function() + " @ " + shot.entry();
            g.drawString(info.substring(0, Math.min(info.length(), 130)), 15, 52);
            g.drawImage(source, 0, header, null);
            g.setColor(new Color(211, 47, 47));
            g.setStroke(new BasicStroke(4));
            g.drawRect(2, header + 2, source.getWidth() - 4, source.getHeight() - 4);
        } finally { g.dispose(); }
        return image;
    }
}
