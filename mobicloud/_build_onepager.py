#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Génère un PDF propre, operator-ready, du one-pager (notes internes retirées).
Usage : python _build_onepager.py [NomOperateur]   (défaut : Mobilis)
"""
import os
import sys
import subprocess
import markdown

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "commercial-pdf")
os.makedirs(OUT, exist_ok=True)

OP = sys.argv[1] if len(sys.argv) > 1 else "Mobilis"
SRC = os.path.join(BASE, "07-execution", "operateur-one-pager.md")

CSS = """
@page { size: A4; margin: 16mm 16mm; }
* { box-sizing: border-box; }
body { font-family: "Segoe UI", "Calibri", Arial, sans-serif; font-size: 10.5pt;
       line-height: 1.5; color: #1a1a1a; }
h1 { font-size: 19pt; color: #0b3d66; border-bottom: 3px solid #0b3d66;
     padding-bottom: 6px; margin-top: 0; }
h2 { font-size: 13pt; color: #0b3d66; margin-top: 18px; }
h3 { font-size: 11pt; color: #14692e; margin-top: 12px; }
table { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 9pt; }
th, td { border: 1px solid #c2cfda; padding: 5px 7px; text-align: left; vertical-align: top; }
th { background: #0b3d66; color: #fff; font-weight: 600; }
tr:nth-child(even) td { background: #f4f8fb; }
blockquote { border-left: 4px solid #14692e; margin: 10px 0; padding: 6px 14px;
             background: #f1f8f2; color: #14400f; font-style: italic; font-size: 11pt; }
strong { color: #0b3d66; }
ul, ol { margin: 6px 0; padding-left: 20px; }
li { margin: 2px 0; }
hr { border: none; border-top: 1px solid #cdd9e3; margin: 14px 0; }
.footer { margin-top: 22px; padding-top: 8px; border-top: 1px solid #cdd9e3;
          font-size: 8pt; color: #7a8a99; text-align: center; }
"""

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

def main():
    with open(SRC, "r", encoding="utf-8") as f:
        lines = f.read().splitlines()
    # Retirer les notes internes (bloc d'instruction en tête)
    clean = []
    for ln in lines:
        s = ln.strip()
        if s.startswith(">") and ("Document commercial" in ln or "Remplacer" in ln
                                   or "laisser après le RDV" in ln):
            continue
        clean.append(ln)
    text = "\n".join(clean)
    text = text.replace("[Opérateur]", OP).replace(" — One-Pager", "")

    html_body = markdown.markdown(text, extensions=["tables", "sane_lists"])
    html = f"""<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8">
<title>MobiCloud x {OP}</title><style>{CSS}</style></head><body>
{html_body}
<div class="footer">MobiCloud — Stockage distribué souverain · Document confidentiel · naoui.tarekanis@gmail.com</div>
</body></html>"""
    outname = f"MobiCloud_OnePager_{OP}"
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
