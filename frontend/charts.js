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

export function generateCharts(subjects) {
    if (!Array.isArray(subjects) || subjects.length === 0) return;

    // ⚡ Bolt: 合併多次 .map() 為單一迴圈以降低記憶體分配與迴圈開銷
    const len = subjects.length;
    const labels = new Array(len);
    const myScores = new Array(len);
    const avgScores = new Array(len);

    for (let i = 0; i < len; i++) {
        const subject = subjects[i];
        labels[i] = shortenName(subject.SubjectName);
        myScores[i] = subject.scoreValue ?? getNumericScore(subject.ScoreDisplay, subject.Score);
        avgScores[i] = subject.classAvgValue ?? getNumericScore(subject.ClassAVGScoreDisplay, subject.ClassAVGScore);
    }

    const renderToken = ++latestRenderToken;

    ensureChartJsLoaded()
        .then((ChartCtor) => {
            if (renderToken !== latestRenderToken) return;
            updateRadarChart(ChartCtor, labels, myScores, avgScores);
            updateBarChart(ChartCtor, labels, myScores, avgScores);
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

function updateRadarChart(ChartCtor, labels, myScores, avgScores) {
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
                        pointBorderColor: '#fff',
                        pointHoverBackgroundColor: '#fff',
                        pointHoverBorderColor: '#6366f1'
                    },
                    {
                        label: '班級平均',
                        data: avgScores,
                        backgroundColor: 'rgba(16, 185, 129, 0.3)',
                        borderColor: '#10b981',
                        borderWidth: 2,
                        pointBackgroundColor: '#10b981',
                        pointBorderColor: '#fff',
                        pointHoverBackgroundColor: '#fff',
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
                            color: '#94a3b8',
                            backdropColor: 'transparent'
                        },
                        grid: {
                            color: 'rgba(148, 163, 184, 0.2)'
                        },
                        angleLines: {
                            color: 'rgba(148, 163, 184, 0.2)'
                        },
                        pointLabels: {
                            color: '#f8fafc',
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
                            color: '#f8fafc',
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

function updateBarChart(ChartCtor, labels, myScores, avgScores) {
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
                            color: 'rgba(148, 163, 184, 0.1)'
                        },
                        ticks: {
                            color: '#94a3b8'
                        }
                    },
                    y: {
                        beginAtZero: true,
                        max: 100,
                        grid: {
                            color: 'rgba(148, 163, 184, 0.1)'
                        },
                        ticks: {
                            color: '#94a3b8'
                        }
                    }
                },
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            color: '#f8fafc',
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

function clearCanvas(canvasId) {
    const canvas = document.getElementById(canvasId);
    const context = canvas?.getContext?.('2d');
    if (!canvas || !context) return;
    context.clearRect(0, 0, canvas.width, canvas.height);
}
