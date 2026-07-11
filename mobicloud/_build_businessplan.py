#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Génère un PDF propre et professionnel de la synthèse business plan (réunion ministère)."""
import os
import subprocess
import markdown

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "commercial-pdf")
os.makedirs(OUT, exist_ok=True)

SRC = os.path.join(BASE, "07-execution", "business-plan-synthese.md")

CSS = """
@page { size: A4; margin: 18mm 17mm; }
* { box-sizing: border-box; }
body { font-family: "Segoe UI", "Calibri", Arial, sans-serif; font-size: 10.5pt;
       line-height: 1.5; color: #1a1a1a; }
h1 { font-size: 21pt; color: #0b3d66; border-bottom: 3px solid #0b3d66;
     padding-bottom: 7px; margin-top: 0; }
h2 { font-size: 13.5pt; color: #0b3d66; margin-top: 20px;
     border-bottom: 1px solid #cdd9e3; padding-bottom: 3px; }
h3 { font-size: 11pt; color: #14692e; margin-top: 13px; }
table { border-collapse: collapse; width: 100%; margin: 9px 0; font-size: 9pt; }
th, td { border: 1px solid #c2cfda; padding: 5px 8px; text-align: left; vertical-align: top; }
th { background: #0b3d66; color: #fff; font-weight: 600; }
tr:nth-child(even) td { background: #f4f8fb; }
blockquote { border-left: 4px solid #14692e; margin: 12px 0; padding: 8px 16px;
             background: #f1f8f2; color: #14400f; font-style: italic; font-size: 11pt; }
strong { color: #0b3d66; }
ul, ol { margin: 6px 0; padding-left: 22px; }
li { margin: 2px 0; }
hr { border: none; border-top: 1px solid #cdd9e3; margin: 16px 0; }
.footer { margin-top: 24px; padding-top: 8px; border-top: 1px solid #cdd9e3;
          font-size: 8pt; color: #7a8a99; text-align: center; }
"""

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

def main():
    with open(SRC, "r", encoding="utf-8") as f:
        text = f.read()
    html_body = markdown.markdown(text, extensions=["tables", "sane_lists"])
    html = f"""<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8">
<title>MobiCloud — Synthèse Business Plan</title><style>{CSS}</style></head><body>
{html_body}
<div class="footer">MobiCloud — Stockage distribué souverain · Synthèse Business Plan · Document confidentiel</div>
</body></html>"""
    outname = "MobiCloud_BusinessPlan_Synthese"
    html_path = os.path.join(OUT, outname + ".html")
    pdf_path = os.path.join(OUT, outname + ".pdf")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html)
    subprocess.run([
        CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer",
        f"--print-to-pdf={pdf_path}", "file:///" + html_path.replace(os.sep, "/"),
    ], check=True, timeout=120)
    os.remove(html_path)
    print("OK ->", pdf_path)

if __name__ == "__main__":
    main()
