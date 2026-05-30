import re
import os
import subprocess

def process_html():
    html_path = "chapter5_body.html"
    if not os.path.exists(html_path):
        print(f"Error: {html_path} does not exist!")
        return None
        
    with open(html_path, "r", encoding="utf-8") as f:
        body_content = f.read()

    # Regex to find blockquotes and convert GitHub-style alerts into beautiful callouts
    def replace_blockquote(match):
        content = match.group(1)
        # Check if it starts with [!ALERT_TYPE]
        alert_match = re.search(r"^\s*<p>\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*", content, re.IGNORECASE)
        if alert_match:
            alert_type = alert_match.group(1).upper()
            # Clean up the marker
            cleaned_content = content.replace(alert_match.group(0), "<p>")
            
            # Map alert types to display titles and classes
            display_title = alert_type
            if alert_type == "NOTE":
                display_title = "💡 Remarque"
                css_class = "callout-note"
            elif alert_type == "TIP":
                display_title = "⭐ Conseil"
                css_class = "callout-tip"
            elif alert_type == "IMPORTANT":
                display_title = "📢 Important"
                css_class = "callout-important"
            elif alert_type == "WARNING":
                display_title = "⚠️ Avertissement"
                css_class = "callout-warning"
            elif alert_type == "CAUTION":
                display_title = "🛑 Attention"
                css_class = "callout-caution"
            else:
                css_class = "callout-note"
                
            return f'<blockquote class="{css_class}"><div class="callout-title">{display_title}</div>{cleaned_content}</blockquote>'
        return match.group(0)

    # Process blockquotes
    processed_body = re.sub(r"<blockquote>([\s\S]*?)<\/blockquote>", replace_blockquote, body_content)

    # Wrap in our premium HTML template
    premium_template = """<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <title>MobiCloud - Chapitre 5 : Réalisation et Implémentation</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Outfit:wght@400;500;600;700;800&family=Lora:ital,wght@0,400;0,500;0,600;1,400&display=swap" rel="stylesheet">
  <script>
    window.MathJax = {
      tex: {
        inlineMath: [['$', '$'], ['\\\\(', '\\\\)']],
        displayMath: [['$$', '$$'], ['\\\\[', '\\\\]']],
        processEscapes: true
      },
      options: {
        skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
      }
    };
  </script>
  <script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
  <style>
    :root {
      --primary: #0f172a;
      --primary-light: #1e293b;
      --accent: #4f46e5;
      --accent-light: #e0e7ff;
      --text: #334155;
      --text-light: #64748b;
      --bg: #ffffff;
      --border: #e2e8f0;
      
      --note-color: #2563eb;
      --note-bg: #eff6ff;
      --tip-color: #16a34a;
      --tip-bg: #f0fdf4;
      --warning-color: #d97706;
      --warning-bg: #fef3c7;
      --danger-color: #dc2626;
      --danger-bg: #fef2f2;
    }

    @media print {
      body {
        background-color: #ffffff;
        -webkit-print-color-adjust: exact;
        print-color-adjust: exact;
      }
      .page-break {
        page-break-before: always;
        break-before: page;
      }
    }

    body {
      font-family: 'Lora', Georgia, serif;
      color: var(--text);
      line-height: 1.7;
      font-size: 11pt;
      margin: 0;
      padding: 0;
      background-color: var(--bg);
    }

    /* Print page setup */
    @page {
      size: A4;
      margin: 25mm 20mm 25mm 20mm;
    }

    /* Content styling */
    .content {
      padding: 0 10px;
    }

    /* Headings */
    h1, h2, h3, h4, h5, h6 {
      font-family: 'Outfit', 'Inter', sans-serif;
      color: var(--primary);
      font-weight: 700;
      line-height: 1.25;
      margin-top: 1.8em;
      margin-bottom: 0.6em;
      page-break-after: avoid;
      break-after: avoid;
    }

    h1 {
      font-size: 24pt;
      border-bottom: 2.5px solid var(--accent);
      padding-bottom: 0.3em;
      margin-top: 0;
    }

    h2 {
      font-size: 16pt;
      border-bottom: 1px solid var(--border);
      padding-bottom: 0.2em;
      margin-top: 2em;
      page-break-before: always;
      break-before: page;
    }

    h3 {
      font-size: 13pt;
      color: var(--primary-light);
      margin-top: 1.5em;
    }

    h4 {
      font-size: 11.5pt;
      color: var(--text);
      font-weight: 600;
    }

    p {
      margin-top: 0;
      margin-bottom: 1.2em;
      text-align: justify;
    }

    strong {
      color: var(--primary);
      font-weight: 600;
      font-family: 'Inter', sans-serif;
    }

    /* Title Page */
    .title-page {
      page-break-after: always;
      break-after: page;
      height: 90vh;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      box-sizing: border-box;
      padding: 40mm 15mm 20mm 15mm;
    }

    .title-header {
      border-left: 6px solid var(--accent);
      padding-left: 30px;
      margin-top: 10vh;
    }

    .title-project {
      font-family: 'Outfit', sans-serif;
      font-size: 16pt;
      font-weight: 800;
      color: var(--accent);
      text-transform: uppercase;
      letter-spacing: 0.18em;
      margin-bottom: 15px;
    }

    .title-main {
      font-family: 'Outfit', sans-serif;
      font-size: 34pt;
      font-weight: 800;
      color: var(--primary);
      line-height: 1.2;
      margin: 0 0 20px 0;
      border: none;
      padding: 0;
    }

    .title-subtitle {
      font-family: 'Inter', sans-serif;
      font-size: 16pt;
      color: var(--text-light);
      font-weight: 300;
      margin: 0;
    }

    .title-footer {
      font-family: 'Inter', sans-serif;
      border-top: 1px solid var(--border);
      padding-top: 25px;
      margin-top: auto;
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
    }

    .title-meta-item {
      font-size: 8.5pt;
      font-weight: 600;
      color: var(--text-light);
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    .title-meta-value {
      font-size: 12pt;
      font-weight: 600;
      color: var(--primary);
      margin-top: 5px;
    }

    /* Code Blocks */
    pre {
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 14px 18px;
      overflow-x: auto;
      margin: 1.5em 0;
      page-break-inside: avoid;
      break-inside: avoid;
    }

    code {
      font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
      font-size: 9pt;
      background-color: #f1f5f9;
      padding: 2px 5px;
      border-radius: 4px;
      color: #0f172a;
    }

    pre code {
      background-color: transparent;
      padding: 0;
      border-radius: 0;
      color: inherit;
      display: block;
      line-height: 1.6;
    }

    /* Tables */
    table {
      width: 100%;
      border-collapse: collapse;
      margin: 2em 0;
      font-family: 'Inter', sans-serif;
      font-size: 9.5pt;
      page-break-inside: avoid;
      break-inside: avoid;
    }

    th, td {
      padding: 12px 14px;
      text-align: left;
      border-bottom: 1px solid var(--border);
    }

    th {
      background-color: #f8fafc;
      color: var(--primary);
      font-weight: 600;
      border-bottom: 2px solid var(--border);
      text-transform: uppercase;
      font-size: 8pt;
      letter-spacing: 0.06em;
    }

    tr:nth-child(even) td {
      background-color: #f8fafc;
    }

    /* Callouts (GitHub alerts) */
    blockquote {
      margin: 1.8em 0;
      padding: 16px 22px;
      border-radius: 0 8px 8px 0;
      font-family: 'Inter', sans-serif;
      font-size: 10pt;
      page-break-inside: avoid;
      break-inside: avoid;
    }

    blockquote p {
      margin: 0;
      text-align: left;
      line-height: 1.6;
    }

    .callout-note {
      border-left: 4px solid var(--note-color);
      background-color: var(--note-bg);
      color: #1e3a8a;
    }
    
    .callout-tip {
      border-left: 4px solid var(--tip-color);
      background-color: var(--tip-bg);
      color: #064e3b;
    }

    .callout-warning {
      border-left: 4px solid var(--warning-color);
      background-color: var(--warning-bg);
      color: #78350f;
    }

    .callout-important {
      border-left: 4px solid var(--danger-color);
      background-color: var(--danger-bg);
      color: #7f1d1d;
    }

    .callout-caution {
      border-left: 4px solid var(--danger-color);
      background-color: var(--danger-bg);
      color: #7f1d1d;
    }

    .callout-title {
      font-weight: 700;
      margin-bottom: 6px;
      text-transform: uppercase;
      font-size: 8.5pt;
      letter-spacing: 0.06em;
    }

    /* Lists */
    ul, ol {
      margin-top: 0;
      margin-bottom: 1.2em;
      padding-left: 24px;
    }

    li {
      margin-bottom: 0.5em;
    }

    li p {
      margin-bottom: 0;
    }

    /* Horizontal lines */
    hr {
      border: 0;
      height: 1px;
      background: var(--border);
      margin: 2.5em 0;
    }
  </style>
</head>
<body>

  <!-- PAGE DE GARDE -->
  <div class="title-page">
    <div class="title-header">
      <div class="title-project">Projet de Fin d'Études — MOBICLOUD</div>
      <h1 class="title-main">Chapitre 5 — Réalisation et Implémentation</h1>
      <p class="title-subtitle">Rapport d'implémentation et de validation technique</p>
    </div>
    
    <div class="title-footer">
      <div>
        <div class="title-meta-item">PRÉPARÉ PAR</div>
        <div class="title-meta-value">Tarek Naoui</div>
      </div>
      <div style="text-align: right;">
        <div class="title-meta-item">DATE</div>
        <div class="title-meta-value">Mai 2026</div>
      </div>
    </div>
  </div>

  <!-- CONTENU PRINCIPAL -->
  <div class="content">
    {CONTENT}
  </div>

</body>
</html>
"""
    
    full_html = premium_template.replace("{CONTENT}", processed_body)
    
    output_html_path = "chapter5_realization.html"
    with open(output_html_path, "w", encoding="utf-8") as f:
        f.write(full_html)
        
    print(f"Success: Generated premium HTML at {output_html_path}")
    return output_html_path

def convert_to_pdf():
    html_path = os.path.abspath("chapter5_realization.html")
    pdf_path = os.path.abspath("chapter5_realization.pdf")
    
    edge_executable = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    if not os.path.exists(edge_executable):
        print("Error: Microsoft Edge could not be found at standard location.")
        return False
        
    # Command to run Edge in headless mode and print HTML to PDF
    # --print-to-pdf creates the PDF at the specified path
    # --print-to-pdf-no-header disables the default browser header/footer
    cmd = [
        edge_executable,
        "--headless",
        "--disable-gpu",
        "--print-to-pdf-no-header",
        f"--print-to-pdf={pdf_path}",
        html_path
    ]
    
    print(f"Running command: {' '.join(cmd)}")
    try:
        # Use subprocess to run the command and wait for it
        result = subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if os.path.exists(pdf_path):
            print(f"Success: Generated PDF at {pdf_path} (size: {os.path.getsize(pdf_path)} bytes)")
            return True
        else:
            print("Error: Command finished but PDF was not generated.")
            return False
    except subprocess.CalledProcessError as e:
        print(f"Error executing Edge: {e}")
        print(f"Stderr: {e.stderr.decode('utf-8', errors='ignore')}")
        return False

if __name__ == "__main__":
    html_file = process_html()
    if html_file:
        convert_to_pdf()
