#!/usr/bin/env python3
"""Install capture_decompiler_png/capture_evidence into a bethington/ghidra-mcp checkout."""
from __future__ import annotations
import argparse
from pathlib import Path
import shutil
import re
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('repo', type=Path, help='Path to bethington/ghidra-mcp clone')
    args = parser.parse_args()
    repo = args.repo.expanduser().resolve()
    plugin = repo / 'src/main/java/com/xebyte/GhidraMCPPlugin.java'
    java_dst = repo / 'src/main/java/com/xebyte/core/DecompilerCaptureService.java'
    java_src = Path(__file__).resolve().parent / 'src/main/java/com/xebyte/core/DecompilerCaptureService.java'
    if not plugin.is_file():
        sys.exit(f'ERROR: not a recognized checkout: {plugin}')
    original = plugin.read_text(encoding='utf-8')
    if 'DecompilerCaptureService' in original:
        print('Already installed; no changes made.')
        return 0
    if java_dst.exists():
        sys.exit(f'ERROR: existing file would be overwritten: {java_dst}')

    changes = [
      (
        '    private final com.xebyte.core.GuiToolService guiToolService;',
        '    private final com.xebyte.core.GuiToolService guiToolService;\n'
        '    private final com.xebyte.core.DecompilerCaptureService captureService;',
      ),
      (
        '        this.guiToolService = new com.xebyte.core.GuiToolService(tool);',
        '        this.guiToolService = new com.xebyte.core.GuiToolService(tool);\n'
        '        this.captureService = new com.xebyte.core.DecompilerCaptureService(tool, guiToolService);',
      ),
    ]
    modified = original
    for before, after in changes:
        n = modified.count(before)
        if n != 1:
            sys.exit(f'ERROR: expected one occurrence, found {n}: {before[:75]!r}.\n'
                     'This GhidraMCP version may need a manual integration.')
        modified = modified.replace(before, after, 1)

    # Indentation differs slightly across releases; insert after the GUI tool service.
    pattern = r'(?m)^([ \t]*)guiToolService,\n(?=[ \t]*new com\.xebyte\.core\.DocumentationApplyService\()'
    modified, count = re.subn(pattern, lambda m: m.group(1) + 'guiToolService,\n' +
                               m.group(1) + 'captureService,\n', modified)
    if count != 1:
        sys.exit(f'ERROR: expected one buildScanner insertion point, found {count}')

    # Write the Java class first; don't modify the existing source unless copying succeeds.
    java_dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(java_src, java_dst)
    try:
        plugin.write_text(modified, encoding='utf-8')
    except Exception:
        java_dst.unlink(missing_ok=True)
        raise
    print('Installed:')
    print(f'  {java_dst.relative_to(repo)}')
    print(f'  {plugin.relative_to(repo)} (3 small edits)')
    print('Next: rebuild and deploy the GhidraMCP extension; restart Ghidra and MCP bridge.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
