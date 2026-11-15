"""Host tests for dump_screen.py. Run: python -m unittest discover -s tools -v"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import dump_screen

RAW = """<?xml version="1.0"?>
<screen package="com.instagram.android" appVersion="1.2.3" source="device" owner="someone">
  <node id="" class="android.widget.FrameLayout" selected="false" visible="true"
        text="a private message" content-desc="Photo by a friend">
    <node id="com.instagram.android:id/clips_tab" class="android.widget.FrameLayout"
          selected="true" visible="true" clickable="true" bounds="[0,0][10,10]" text="Reels"/>
    <secret>not a node</secret>
  </node>
</screen>"""


class DumpScreenTests(unittest.TestCase):
    def test_sanitize_drops_text_and_unknown_attributes(self):
        screen = dump_screen.sanitize(RAW)
        attributes = {key for element in screen.iter() for key in element.attrib}
        self.assertNotIn("text", attributes)
        self.assertNotIn("content-desc", attributes)
        self.assertNotIn("owner", attributes)
        self.assertEqual([], [element.tag for element in screen.iter() if element.tag not in ("screen", "node")])

    def test_sanitize_keeps_what_the_matcher_reads(self):
        screen = dump_screen.sanitize(RAW)
        self.assertEqual("com.instagram.android", screen.get("package"))
        tab = list(screen.iter("node"))[1]
        self.assertEqual("com.instagram.android:id/clips_tab", tab.get("id"))
        self.assertEqual("true", tab.get("selected"))

    def test_summary_reports_selected_and_interesting_ids(self):
        summary = "\n".join(dump_screen.summarize(dump_screen.sanitize(RAW)))
        self.assertIn("selected ids: clips_tab", summary)
        self.assertIn("ids worth a look: clips_tab", summary)

    def test_rejects_a_file_that_is_not_a_dump(self):
        with self.assertRaises(ValueError):
            dump_screen.sanitize("<hierarchy/>")


if __name__ == "__main__":
    unittest.main()
