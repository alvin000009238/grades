import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
    root: 'frontend',
    build: {
        outDir: resolve(__dirname, 'public/dist'),
        emptyOutDir: true,
        manifest: true,
        rollupOptions: {
            input: resolve(__dirname, 'frontend/main.js'),
            output: {
                entryFileNames: 'main-[hash].js',
                chunkFileNames: '[name]-[hash].js',
                assetFileNames: '[name]-[hash][extname]',
            },
        },
    },
});
