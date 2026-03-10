import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
    root: 'frontend',
    build: {
        outDir: resolve(__dirname, 'public/dist'),
        emptyOutDir: true,
        rollupOptions: {
            input: resolve(__dirname, 'frontend/main.js'),
            output: {
                entryFileNames: 'main.js',
                chunkFileNames: '[name].js',
                assetFileNames: '[name][extname]',
            },
        },
    },
});
