#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Convertit les fichiers Markdown importants de MobiCloud en PDF stylés (présentation mentor)."""
import os
import subprocess
import sys
import markdown

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "presentation-pdf")
os.makedirs(OUT, exist_ok=True)

# (chemin relatif, titre du PDF, nom de fichier de sortie)
FILES = [
    ("PROGRESS.md", "MobiCloud — Vue d'ensemble du projet", "00_Vue-ensemble"),
    ("02-strategy/lean-canvas.md", "MobiCloud — Lean Canvas", "01_Lean-Canvas"),
    ("02-strategy/positioning.md", "MobiCloud — Positionnement", "02_Positionnement"),
    ("02-strategy/business-model.md", "MobiCloud — Modèle Économique", "03_Modele-economique"),
    ("02-strategy/operator-model.md", "MobiCloud — Modèle Opérateur (B2B2C)", "06_Modele-operateur"),
    ("02-strategy/contributor-incentives.md", "MobiCloud — Récompense des Contributeurs", "07_Recompense-contributeurs"),
    ("05-financial/projections.md", "MobiCloud — Projections Financières", "04_Projections-financieres"),
    ("06-validation/validation-plan.md", "MobiCloud — Plan de Validation", "05_Plan-validation"),
]

CSS = """
@page { size: A4; margin: 18mm 16mm; }
* { box-sizing: border-box; }
body {
  font-family: "Segoe UI", "Calibri", Arial, sans-serif;
  font-size: 10.5pt; line-height: 1.5; color: #1a1a1a; max-width: 100%;
}
h1 { font-size: 20pt; color: #0b3d66; border-bottom: 3px solid #0b3d66;
     padding-bottom: 6px; margin-top: 0; }
h2 { font-size: 14pt; color: #0b3d66; margin-top: 22px;
     border-bottom: 1px solid #cdd9e3; padding-bottom: 3px; }
h3 { font-size: 11.5pt; color: #14692e; margin-top: 16px; }
table { border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 9pt; }
th, td { border: 1px solid #c2cfda; padding: 5px 7px; text-align: left;
         vertical-align: top; }
th { background: #0b3d66; color: #fff; font-weight: 600; }
tr:nth-child(even) td { background: #f4f8fb; }
code { background: #eef2f5; padding: 1px 4px; border-radius: 3px;
       font-family: Consolas, monospace; font-size: 9pt; }
pre { background: #f4f8fb; border: 1px solid #cdd9e3; border-radius: 5px;
      padding: 10px; overflow-x: auto; font-size: 8.5pt; }
pre code { background: none; }
blockquote { border-left: 4px solid #14692e; margin: 10px 0; padding: 4px 14px;
             background: #f1f8f2; color: #2c3e2c; font-style: italic; }
strong { color: #0b3d66; }
ul, ol { margin: 6px 0; padding-left: 22px; }
li { margin: 2px 0; }
hr { border: none; border-top: 1px solid #cdd9e3; margin: 18px 0; }
a { color: #0b3d66; text-decoration: none; }
.footer { margin-top: 26px; padding-top: 8px; border-top: 1px solid #cdd9e3;
          font-size: 8pt; color: #7a8a99; text-align: center; }
"""

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

def build(rel, title, outname):
    src = os.path.join(BASE, rel.replace("/", os.sep))
    with open(src, "r", encoding="utf-8") as f:
        text = f.read()
    html_body = markdown.markdown(
        text, extensions=["tables", "fenced_code", "toc", "sane_lists"]
    )
    html = f"""<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8">
<title>{title}</title><style>{CSS}</style></head><body>
{html_body}
<div class="footer">MobiCloud — PFE — Document de présentation · généré le 2026-06-22</div>
</body></html>"""
    html_path = os.path.join(OUT, outname + ".html")
    pdf_path = os.path.join(OUT, outname + ".pdf")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html)
    subprocess.run([
        CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer",
        f"--print-to-pdf={pdf_path}", "file:///" + html_path.replace(os.sep, "/"),
    ], check=True, timeout=120)
    os.remove(html_path)
    print("OK ->", os.path.basename(pdf_path))

if __name__ == "__main__":
    for rel, title, outname in FILES:
        build(rel, title, outname)
    print("\nTous les PDF sont dans:", OUT)
