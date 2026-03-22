import re
import urllib.request

HTML_PATH = 'public/index.html'

def get_svg(icon_name):
    # Try different sources or a known fallback if it changes. The MD icons github is very stable.
    url = f"https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{icon_name}/materialsymbolsoutlined/{icon_name}_24px.svg"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            svg = response.read().decode('utf-8')
            # Extract the inner <path> or content, or just change the outer <svg>
            # The returned SVG is typically:
            # <svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24"><path d="..."/></svg>
            # We want to add fill="currentColor" to make it inherit text color.
             # and remove fixed height/width so it scales with font-size if needed, or keep 1em
            svg = re.sub(r'height="\d+"', 'height="1em"', svg)
            svg = re.sub(r'width="\d+"', 'width="1em"', svg)
            svg = svg.replace('<svg ', '<svg fill="currentColor" ')
            return svg.strip()
    except Exception as e:
        print(f"Failed to fetch {icon_name}: {e}")
        return None

with open(HTML_PATH, 'r', encoding='utf-8') as f:
    html = f.read()

# 1. Replace the Google Fonts blocking tag
fonts_old = r'<link\s*href="https://fonts.googleapis.com/css2\?family=Roboto[^"]+"\s*rel="stylesheet">'
fonts_new = r'''<link rel="preload" as="style" href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;600;700&family=Noto+Sans+TC:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;600;700&family=Noto+Sans+TC:wght@400;500;600;700&display=swap" media="print" onload="this.media='all'">
    <noscript><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;600;700&family=Noto+Sans+TC:wght@400;500;600;700&display=swap"></noscript>'''
html = re.sub(fonts_old, fonts_new, html, flags=re.MULTILINE|re.DOTALL)

# 2. Remove Material Symbols link
icons_old = r'<link\s*href="https://fonts.googleapis.com/css2\?family=Material\+Symbols\+Outlined[^"]+"\s*rel="stylesheet">\n?'
html = re.sub(icons_old, '', html, flags=re.MULTILINE|re.DOTALL)

# 3. Find and replace Material Symbols spans
# format is <span class="material-symbols-outlined" ...>icon_name</span>
# or <span class="material-symbols-outlined emoji-icon">icon_name</span>
pattern = r'<span class="material-symbols-outlined(.*?)">(.*?)</span>'

def substitute_icon(match):
    attrs = match.group(1).strip()
    icon_name = match.group(2).strip()
    svg = get_svg(icon_name)
    if svg:
        # Wrap SVG in a span that preserves the original styling and classes
        # But we don't need 'material-symbols-outlined' anymore.
        # It's better to just give it a generic icon class if there were others, like 'eye-icon'
        # e.g. class="material-symbols-outlined eye-icon" -> class="eye-icon"
        # wait, just replacing the internal text with SVG is easiest!
        # But we don't need the font-family anymore.
        # Let's keep the wrap but remove material-symbols-outlined class so it doesn't try to use the font.
        # Wait, if we remove the class, it might lose `display: inline-block` or flex alignment properties if they relied on it? 
        # Actually, adding `class="svg-icon"` and replacing material-symbols-outlined:
        # SVG可以吃 classes 和 styles
        
        # Merge classes: exclude material-symbols-outlined

        svg_with_attrs = svg.replace('<svg ', f'<svg class="inline-svg-icon" {attrs} ')
        return svg_with_attrs
    else:
        # Fallback if download fails
        return match.group(0)

print("Downloading icon SVGs and modifying HTML...")
html = re.sub(pattern, substitute_icon, html)

with open(HTML_PATH, 'w', encoding='utf-8') as f:
    f.write(html)

print("Done! HTML updated.")
