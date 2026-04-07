import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const projectRoot = path.resolve(__dirname, '..');
const manifestPath = path.resolve(projectRoot, 'public/dist/.vite/manifest.json');
const htmlPaths = [
    path.resolve(projectRoot, 'public/index.html'),
    path.resolve(projectRoot, 'public/privacy.html')
];

try {
    // 1. Read manifest.json
    if (!fs.existsSync(manifestPath)) {
        console.error(`Manifest file not found at ${manifestPath}`);
        process.exit(1);
    }
    
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
    
    htmlPaths.forEach(htmlPath => {
        if (!fs.existsSync(htmlPath)) {
            return;
        }

        let html = fs.readFileSync(htmlPath, 'utf-8');

        // Iterate over manifest keys to find entry points and inject their new names
        Object.keys(manifest).forEach(key => {
        const entryData = manifest[key];
        if (entryData && (entryData.isEntry || entryData.isDynamicEntry) && entryData.file) {
            // chunk name could be 'main.js' or 'frontend/main.js'
            const baseName = key.split('/').pop().replace(/\.js$/, '');
            
            console.log(`[Hash Injector] Found Entry JS for ${baseName}: ${entryData.file}`);
            
            // Replaces both original `<script src="/dist/main.js">` 
            // and already-hashed `<script src="/dist/main-ASD.js">`
            // The regex dynamically builds pattern to boundary-check baseName
            const scriptRegex = new RegExp(`<script\\s+type="module"\\s+src="\\/dist\\/${baseName}(-[a-zA-Z0-9_-]+)?\\.js"><\\/script>`, 'g');
            html = html.replace(scriptRegex, `<script type="module" src="/dist/${entryData.file}"></script>`);

            if (entryData.css && entryData.css.length > 0) {
                const cssFile = entryData.css[0];
                const cssRegex = /<link[^>]+id="vite-css"[^>]*>/g;
                html = html.replace(cssRegex, `<link rel="stylesheet" href="/dist/${cssFile}" id="vite-css">`);
            }
        }
        });

        fs.writeFileSync(htmlPath, html, 'utf-8');
        console.log(`[Hash Injector] Successfully updated ${path.relative(projectRoot, htmlPath)}`);
    });
    
} catch (error) {
    console.error('[Hash Injector] Error:', error);
    process.exit(1);
}
