import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const projectRoot = path.resolve(__dirname, '..');
const manifestPath = path.resolve(projectRoot, 'public/dist/.vite/manifest.json');
const htmlPath = path.resolve(projectRoot, 'public/index.html');

try {
    // 1. Read manifest.json
    if (!fs.existsSync(manifestPath)) {
        console.error(`Manifest file not found at ${manifestPath}`);
        process.exit(1);
    }
    
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
    
    // 2. Find the entry file path
    const entryData = manifest['main.js'] || manifest['frontend/main.js'];
    if (!entryData || !entryData.file) {
        console.error('main.js entry not found in manifest.json!');
        process.exit(1);
    }
    
    const hashedFilename = entryData.file;
    console.log(`[Hash Injector] Found main JS with hash: ${hashedFilename}`);
    
    // 3. Update index.html
    let html = fs.readFileSync(htmlPath, 'utf-8');
    
    // Replace <script type="module" src="/dist/main.js"></script> 
    // or <script type="module" src="/dist/main-*.js"></script>
    const scriptRegex = /<script\s+type="module"\s+src="\/dist\/main(-[a-zA-Z0-9]+)?\.js"><\/script>/g;
    html = html.replace(scriptRegex, `<script type="module" src="/dist/${hashedFilename}"></script>`);
    
    fs.writeFileSync(htmlPath, html, 'utf-8');
    console.log(`[Hash Injector] Successfully updated public/index.html to use /dist/${hashedFilename}`);
    
} catch (error) {
    console.error('[Hash Injector] Error:', error);
    process.exit(1);
}
