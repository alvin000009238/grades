// ========================================
// Chart.js 圖表生成
// ========================================

import { getNumericScore, shortenName } from './dashboard.js';

const CHART_JS_URL = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js';
const CHART_JS_INTEGRITY = 'sha384-vsrfeLOOY6KuIYKDlmVH5UiBmgIdB1oEf7p01YgWHuqmOHfZr374+odEv96n9tNC';
const CHART_SCRIPT_SELECTOR = 'script[data-chartjs-loader="true"]';

let radarChartInstance = null;
let barChartInstance = null;
let chartJsLoadPromise = null;
let latestRenderToken = 0;
let cachedChartPalette = null;

if (typeof document !== 'undefined') {
    document.addEventListener('themechange', () => {
        cachedChartPalette = null;
        applyThemeToExistingCharts();
    });
}

export function generateCharts(subjects) {
    if (!Array.isArray(subjects) || subjects.length === 0) return;

    const labels = subjects.map((subject) => shortenName(subject.SubjectName));
    const myScores = subjects.map((subject) => subject.scoreValue ?? getNumericScore(subject.ScoreDisplay, subject.Score));
    const avgScores = subjects.map((subject) => subject.classAvgValue ?? getNumericScore(subject.ClassAVGScoreDisplay, subject.ClassAVGScore));
    const palette = getChartThemePalette();
    const renderToken = ++latestRenderToken;

    ensureChartJsLoaded()
        .then((ChartCtor) => {
            if (renderToken !== latestRenderToken) return;

            const palette = getCachedChartThemePalette();
            updateRadarChart(ChartCtor, labels, myScores, avgScores, palette);
            updateBarChart(ChartCtor, labels, myScores, avgScores, palette);
        })
        .catch((error) => {
            console.warn('Failed to load Chart.js', error);
        });
}

export function resetCharts() {
    if (radarChartInstance) {
        radarChartInstance.destroy();
        radarChartInstance = null;
    }
    if (barChartInstance) {
        barChartInstance.destroy();
        barChartInstance = null;
    }
    clearCanvas('radarChart');
    clearCanvas('barChart');
}

function ensureChartJsLoaded() {
    if (typeof window.Chart !== 'undefined') {
        return Promise.resolve(window.Chart);
    }

    if (chartJsLoadPromise) {
        return chartJsLoadPromise;
    }

    chartJsLoadPromise = new Promise((resolve, reject) => {
        const existingScript = document.querySelector(CHART_SCRIPT_SELECTOR);
        if (existingScript) {
            existingScript.addEventListener('load', () => {
                if (typeof window.Chart !== 'undefined') {
                    resolve(window.Chart);
                } else {
                    reject(new Error('Chart.js loaded but window.Chart is unavailable'));
                }
            }, { once: true });
            existingScript.addEventListener('error', () => {
                chartJsLoadPromise = null;
                reject(new Error('Chart.js script load failed'));
            }, { once: true });
            return;
        }

        const script = document.createElement('script');
        script.src = CHART_JS_URL;
        script.integrity = CHART_JS_INTEGRITY;
        script.crossOrigin = 'anonymous';
        script.async = true;
        script.defer = true;
        script.dataset.chartjsLoader = 'true';

        script.onload = () => {
            if (typeof window.Chart !== 'undefined') {
                resolve(window.Chart);
            } else {
                chartJsLoadPromise = null;
                reject(new Error('Chart.js loaded but window.Chart is unavailable'));
            }
        };
        script.onerror = () => {
            chartJsLoadPromise = null;
            reject(new Error('Chart.js script load failed'));
        };

        document.head.appendChild(script);
    });

    return chartJsLoadPromise;
}

function updateRadarChart(ChartCtor, labels, myScores, avgScores, palette) {
    const radarCanvas = document.getElementById('radarChart');
    const radarCtx = radarCanvas?.getContext('2d');
    if (!radarCtx) return;

    if (!radarChartInstance) {
        radarChartInstance = new ChartCtor(radarCtx, {
            type: 'radar',
            data: {
                labels,
                datasets: [
                    {
                        label: '我的成績',
                        data: myScores,
                        backgroundColor: 'rgba(99, 102, 241, 0.3)',
                        borderColor: '#6366f1',
                        borderWidth: 2,
                        pointBackgroundColor: '#6366f1',
                        pointBorderColor: palette.surface,
                        pointHoverBackgroundColor: palette.surface,
                        pointHoverBorderColor: '#6366f1'
                    },
                    {
                        label: '班級平均',
                        data: avgScores,
                        backgroundColor: 'rgba(16, 185, 129, 0.3)',
                        borderColor: '#10b981',
                        borderWidth: 2,
                        pointBackgroundColor: '#10b981',
                        pointBorderColor: palette.surface,
                        pointHoverBackgroundColor: palette.surface,
                        pointHoverBorderColor: '#10b981'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                scales: {
                    r: {
                        beginAtZero: true,
                        max: 100,
                        ticks: {
                            stepSize: 20,
                            color: palette.textMuted,
                            backdropColor: 'transparent'
                        },
                        grid: {
                            color: palette.grid
                        },
                        angleLines: {
                            color: palette.grid
                        },
                        pointLabels: {
                            color: palette.textSecondary,
                            font: {
                                size: 12
                            }
                        }
                    }
                },
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            color: palette.textMain,
                            padding: 20,
                            font: {
                                size: 13
                            }
                        }
                    }
                }
            }
        });
        return;
    }

    radarChartInstance.data.labels = labels;
    radarChartInstance.data.datasets[0].data = myScores;
    radarChartInstance.data.datasets[1].data = avgScores;
    radarChartInstance.update('none');
}

function updateBarChart(ChartCtor, labels, myScores, avgScores, palette) {
    const barCanvas = document.getElementById('barChart');
    const barCtx = barCanvas?.getContext('2d');
    if (!barCtx) return;

    if (!barChartInstance) {
        barChartInstance = new ChartCtor(barCtx, {
            type: 'bar',
            data: {
                labels,
                datasets: [
                    {
                        label: '我的成績',
                        data: myScores,
                        backgroundColor: 'rgba(99, 102, 241, 0.8)',
                        borderColor: '#6366f1',
                        borderWidth: 1,
                        borderRadius: 6
                    },
                    {
                        label: '班級平均',
                        data: avgScores,
                        backgroundColor: 'rgba(16, 185, 129, 0.8)',
                        borderColor: '#10b981',
                        borderWidth: 1,
                        borderRadius: 6
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                scales: {
                    x: {
                        grid: {
                            color: palette.gridSubtle
                        },
                        ticks: {
                            color: palette.textMuted
                        }
                    },
                    y: {
                        beginAtZero: true,
                        max: 100,
                        grid: {
                            color: palette.gridSubtle
                        },
                        ticks: {
                            color: palette.textMuted
                        }
                    }
                },
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            color: palette.textMain,
                            padding: 20,
                            font: {
                                size: 13
                            }
                        }
                    }
                }
            }
        });
        return;
    }

    barChartInstance.data.labels = labels;
    barChartInstance.data.datasets[0].data = myScores;
    barChartInstance.data.datasets[1].data = avgScores;
    barChartInstance.update('none');
}

function getChartThemePalette() {
    const styles = getComputedStyle(document.documentElement);
    const readVar = (name, fallback) => {
        const value = styles.getPropertyValue(name).trim();
        return value || fallback;
    };

    return {
        textMain: readVar('--color-text-main', '#0f172a'),
        textSecondary: readVar('--color-text-secondary', '#334155'),
        textMuted: readVar('--color-text-muted', '#64748b'),
        surface: readVar('--color-surface-elevated', '#ffffff'),
        grid: readVar('--color-border-subtle', 'rgba(148, 163, 184, 0.22)'),
        gridSubtle: readVar('--color-border-extra-subtle', 'rgba(148, 163, 184, 0.16)')
    };
}

function getCachedChartThemePalette() {
    if (!cachedChartPalette) {
        cachedChartPalette = getChartThemePalette();
    }
    return cachedChartPalette;
}

function applyThemeToExistingCharts() {
    const palette = getCachedChartThemePalette();

    if (radarChartInstance) {
        const radarOptions = radarChartInstance.options;
        const radarScale = radarOptions.scales?.r;
        const radarLegendLabels = radarOptions.plugins?.legend?.labels;
        if (radarScale) {
            radarScale.ticks.color = palette.textMuted;
            radarScale.grid.color = palette.grid;
            radarScale.angleLines.color = palette.grid;
            radarScale.pointLabels.color = palette.textSecondary;
        }
        if (radarLegendLabels) {
            radarLegendLabels.color = palette.textMain;
        }

        radarChartInstance.data.datasets.forEach((dataset) => {
            dataset.pointBorderColor = palette.surface;
            dataset.pointHoverBackgroundColor = palette.surface;
        });

        radarChartInstance.update('none');
    }

    if (barChartInstance) {
        const barOptions = barChartInstance.options;
        const xScale = barOptions.scales?.x;
        const yScale = barOptions.scales?.y;
        const barLegendLabels = barOptions.plugins?.legend?.labels;
        if (xScale) {
            xScale.grid.color = palette.gridSubtle;
            xScale.ticks.color = palette.textMuted;
        }
        if (yScale) {
            yScale.grid.color = palette.gridSubtle;
            yScale.ticks.color = palette.textMuted;
        }
        if (barLegendLabels) {
            barLegendLabels.color = palette.textMain;
        }

        barChartInstance.update('none');
    }
}

export function __setChartInstancesForTest(instances = {}) {
    if (Object.hasOwn(instances, 'radar')) radarChartInstance = instances.radar;
    if (Object.hasOwn(instances, 'bar')) barChartInstance = instances.bar;
}

function clearCanvas(canvasId) {
    const canvas = document.getElementById(canvasId);
    const context = canvas?.getContext?.('2d');
    if (!canvas || !context) return;
    context.clearRect(0, 0, canvas.width, canvas.height);
}
