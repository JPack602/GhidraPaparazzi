import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

PATCHER = Path(__file__).resolve().parents[1] / 'apply_addon.py'

# The matching anchors from bethington/ghidra-mcp v7.0.0 are mocked here so the
# installer can be tested independently of the multi-project Ghidra build.
PLUGIN_TEMPLATE = """\
class GhidraMCPPlugin {
    private final com.xebyte.core.GuiToolService guiToolService;

    public GhidraMCPPlugin(Object tool) {
        this.guiToolService = new com.xebyte.core.GuiToolService(tool);
    }

    void buildScanner() {
        new Object[] {
            guiToolService,
            new com.xebyte.core.DocumentationApplyService(),
        };
    }
}
"""


class AddonInstallerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.plugin = self.root / 'src/main/java/com/xebyte/GhidraMCPPlugin.java'
        self.plugin.parent.mkdir(parents=True)
        self.plugin.write_text(PLUGIN_TEMPLATE, encoding='utf-8')
        self.service = self.root / 'src/main/java/com/xebyte/core/DecompilerCaptureService.java'

    def tearDown(self):
        self.temp.cleanup()

    def run_patcher(self):
        return subprocess.run([sys.executable, str(PATCHER), str(self.root)],
                              capture_output=True, text=True)

    def test_install_then_idempotent_reinstall(self):
        first = self.run_patcher()
        self.assertEqual(first.returncode, 0, first.stderr + first.stdout)
        self.assertTrue(self.service.exists())
        patched = self.plugin.read_text(encoding='utf-8')
        self.assertEqual(patched.count('captureService,'), 1)
        self.assertIn('new com.xebyte.core.DecompilerCaptureService(tool, guiToolService)', patched)
        second = self.run_patcher()
        self.assertEqual(second.returncode, 0, second.stderr + second.stdout)
        self.assertEqual(self.plugin.read_text(encoding='utf-8'), patched)

    def test_bad_upstream_structure_changes_nothing(self):
        self.plugin.write_text('class GhidraMCPPlugin {}', encoding='utf-8')
        outcome = self.run_patcher()
        self.assertNotEqual(outcome.returncode, 0)
        self.assertFalse(self.service.exists())
        self.assertEqual(self.plugin.read_text(encoding='utf-8'), 'class GhidraMCPPlugin {}')


if __name__ == '__main__':
    unittest.main()
