// ========================================
// 成績分析儀表板 - JavaScript
// ========================================

// 初始化
let radarChartInstance = null;
let barChartInstance = null;

document.addEventListener('DOMContentLoaded', () => {
    loadGradesData();
    setupFileImport();
});

async function loadGradesData() {
    try {
        const storedData = getStoredGrades();
        if (storedData) {
            initDashboard(storedData);
            return;
        }

        const response = await fetch('GetScoreForStudentExamContent.json');
        if (!response.ok) {
            throw new Error(`資料載入失敗: ${response.status}`);
        }

        const gradesData = await response.json();
        validateGradesData(gradesData);
        initDashboard(gradesData);
    } catch (error) {
        console.error(error);
        document.getElementById('updateTime').textContent = '資料載入失敗';
    }
}

function setupFileImport() {
    const fileInput = document.getElementById('gradesFile');
    if (!fileInput) return;

    fileInput.addEventListener('change', event => {
        const [file] = event.target.files;
        if (!file) return;

        const reader = new FileReader();
        reader.onload = () => {
            try {
                const gradesData = JSON.parse(reader.result);
                validateGradesData(gradesData);
                storeGradesData(gradesData);
                initDashboard(gradesData);
            } catch (error) {
                console.error(error);
                alert(`匯入失敗：${error.message || '檔案格式不正確'}`);
            } finally {
                fileInput.value = '';
            }
        };
        reader.readAsText(file);
    });
}

function validateGradesData(gradesData) {
    if (!gradesData?.Result) {
        throw new Error('缺少 Result 資料');
    }

    if (!Array.isArray(gradesData.Result.SubjectExamInfoList)) {
        throw new Error('缺少 SubjectExamInfoList 成績清單');
    }
}

function storeGradesData(gradesData) {
    localStorage.setItem('gradesData', JSON.stringify(gradesData));
}

function getStoredGrades() {
    const stored = localStorage.getItem('gradesData');
    if (!stored) return null;
    try {
        const parsed = JSON.parse(stored);
        validateGradesData(parsed);
        return parsed;
    } catch (error) {
        console.warn('儲存的成績資料格式不正確，已忽略。', error);
        localStorage.removeItem('gradesData');
        return null;
    }
}

function initDashboard(gradesData) {
    const result = gradesData.Result;
    const standards = Array.isArray(result["成績五標List"]) ? result["成績五標List"] : [];

    // 更新學生資訊
    updateStudentInfo(result);

    // 更新考試資訊
    updateExamInfo(result);

    // 計算統計資料
    updateStatistics(result.SubjectExamInfoList);

    // 生成成績卡片
    generateScoreCards(result.SubjectExamInfoList);

    // 生成圖表
    generateCharts(result.SubjectExamInfoList);

    // 生成五標表格
    generateStandardsTable(result.SubjectExamInfoList, standards);

    // 生成分佈圖
    generateDistributionCards(result.SubjectExamInfoList, standards);
}

// 更新學生資訊
function updateStudentInfo(result) {
    const studentName = result.StudentName || '--';
    document.getElementById('studentName').textContent = studentName;
    document.getElementById('studentClass').textContent = result.StudentClassName || '--';
    document.getElementById('studentSeat').textContent = result.StudentSeatNo || '--';
    document.getElementById('studentNo').textContent = result.StudentNo || '--';
    document.getElementById('avatarText').textContent = studentName.charAt(0) || '--';
    document.getElementById('updateTime').textContent = result.GetDataTimeDisplay || '--';
}

function updateExamInfo(result) {
    const examTitle = document.getElementById('examTitle');
    if (!examTitle) return;

    const termDisplay = result.SubjectExamInfoList?.[0]?.YearTermDisplay;
    const examName = result.ExamItem?.ExamName;
    if (termDisplay && examName) {
        examTitle.textContent = `${termDisplay} ${examName}`;
    } else if (examName) {
        examTitle.textContent = examName;
    }
}

function updateExamInfo(result) {
    const examTitle = document.getElementById('examTitle');
    if (!examTitle) return;

    const termDisplay = result.SubjectExamInfoList?.[0]?.YearTermDisplay;
    const examName = result.ExamItem?.ExamName;
    if (termDisplay && examName) {
        examTitle.textContent = `${termDisplay} ${examName}`;
    } else if (examName) {
        examTitle.textContent = examName;
    }
}

// 計算統計
function updateStatistics(subjects) {
    if (!subjects.length) {
        document.getElementById('avgScore').textContent = '--';
        document.getElementById('totalSubjects').textContent = '0';
        document.getElementById('highestScore').textContent = '--';
        return;
    }
    const scores = subjects.map(subject => getNumericScore(subject.ScoreDisplay, subject.Score));
    const avg = scores.reduce((a, b) => a + b, 0) / scores.length;
    const highest = Math.max(...scores);

    document.getElementById('avgScore').textContent = avg.toFixed(1);
    document.getElementById('totalSubjects').textContent = subjects.length;
    document.getElementById('highestScore').textContent = highest;
}

// 生成成績卡片
function generateScoreCards(subjects) {
    const grid = document.getElementById('scoresGrid');
    grid.innerHTML = '';

    subjects.forEach(subject => {
        const scoreValue = getNumericScore(subject.ScoreDisplay, subject.Score);
        const classAvgValue = getNumericScore(subject.ClassAVGScoreDisplay, subject.ClassAVGScore);
        const diff = scoreValue - classAvgValue;
        const scoreClass = getScoreClass(scoreValue);
        const diffClass = diff >= 0 ? 'positive' : 'negative';
        const diffIcon = diff >= 0 ? '↑' : '↓';

        const card = document.createElement('div');
        card.className = 'score-card';
        card.innerHTML = `
            <div class="score-header">
                <span class="subject-name">${subject.SubjectName}</span>
                <span class="score-badge ${scoreClass}">${subject.ScoreDisplay ?? scoreValue}</span>
            </div>
            <div class="score-details">
                <div class="score-row">
                    <span class="score-label">班級平均</span>
                    <span class="score-value">${subject.ClassAVGScoreDisplay ?? classAvgValue.toFixed(2)}</span>
                </div>
                <div class="score-row">
                    <span class="score-label">與班平均差距</span>
                    <span class="diff-indicator ${diffClass}">
                        ${diffIcon} ${Math.abs(diff).toFixed(2)}
                    </span>
                </div>
                <div class="progress-bar">
                    <div class="progress-fill" style="width: ${scoreValue}%"></div>
                </div>
            </div>
        `;
        grid.appendChild(card);
    });
}

function getScoreClass(score) {
    if (score >= 80) return 'high';
    if (score >= 60) return 'medium';
    return 'low';
}

// 生成圖表
function generateCharts(subjects) {
    const labels = subjects.map(s => shortenName(s.SubjectName));
    const myScores = subjects.map(subject => getNumericScore(subject.ScoreDisplay, subject.Score));
    const avgScores = subjects.map(subject => getNumericScore(subject.ClassAVGScoreDisplay, subject.ClassAVGScore));

    // 雷達圖
    const radarCtx = document.getElementById('radarChart').getContext('2d');
    if (radarChartInstance) {
        radarChartInstance.destroy();
    }
    radarChartInstance = new Chart(radarCtx, {
        type: 'radar',
        data: {
            labels: labels,
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

    // 長條圖
    const barCtx = document.getElementById('barChart').getContext('2d');
    if (barChartInstance) {
        barChartInstance.destroy();
    }
    barChartInstance = new Chart(barCtx, {
        type: 'bar',
        data: {
            labels: labels,
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
}

// 縮短科目名稱
function shortenName(name) {
    const shortNames = {
        '英語文': '英文',
        '公民與社會': '公民',
        '選修化學-物質與能量': '化學',
        '選修物理-力學一': '物理'
    };
    return shortNames[name] || name;
}

// 生成五標表格
function generateStandardsTable(subjects, standards) {
    const tbody = document.getElementById('standardsBody');
    tbody.innerHTML = '';

    subjects.forEach((subject, index) => {
        const std = standards.find(s => cleanSubjectName(s.SubjectName) === subject.SubjectName) || standards[index];
        if (!std) return;

        const score = getNumericScore(subject.ScoreDisplay, subject.Score);
        const level = getScoreLevel(score, std);

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${subject.SubjectName}</td>
            <td class="top-mark">${std["頂標"].toFixed(2)}</td>
            <td class="front-mark">${std["前標"].toFixed(2)}</td>
            <td class="avg-mark">${std["均標"].toFixed(2)}</td>
            <td class="back-mark">${std["後標"].toFixed(2)}</td>
            <td class="bottom-mark">${std["底標"].toFixed(2)}</td>
            <td>${std["標準差"].toFixed(2)}</td>
            <td><span class="my-score ${level.class}">${score}</span></td>
            <td><span class="level-badge ${level.class}">${level.text}</span></td>
        `;
        tbody.appendChild(row);
    });
}

// 清理科目名稱（移除 <br/>）
function cleanSubjectName(name) {
    return name.replace(/<br\/>/g, '');
}

// 取得成績等級
function getScoreLevel(score, std) {
    if (score >= std["頂標"]) return { text: '頂標以上', class: 'excellent' };
    if (score >= std["前標"]) return { text: '前標以上', class: 'good' };
    if (score >= std["均標"]) return { text: '均標以上', class: 'average' };
    if (score >= std["後標"]) return { text: '後標以上', class: 'below' };
    return { text: '底標以下', class: 'poor' };
}

// 生成分佈圖
function generateDistributionCards(subjects, standards) {
    const grid = document.getElementById('distributionGrid');
    grid.innerHTML = '';

    subjects.forEach((subject, index) => {
        const std = standards.find(s => cleanSubjectName(s.SubjectName) === subject.SubjectName) || standards[index];
        if (!std) return;

        const total = std["大於90Count"] + std["大於80Count"] + std["大於70Count"] +
            std["大於60Count"] + std["大於50Count"] + std["大於40Count"] +
            std["大於30Count"] + std["大於20Count"] + std["大於10Count"] + std["大於0Count"];

        const ranges = [
            { label: '90-100', count: std["大於90Count"], class: 'high-score' },
            { label: '80-89', count: std["大於80Count"], class: 'high-score' },
            { label: '70-79', count: std["大於70Count"], class: 'good-score' },
            { label: '60-69', count: std["大於60Count"], class: 'avg-score' },
            { label: '50-59', count: std["大於50Count"], class: 'low-score' },
            { label: '0-49', count: std["大於40Count"] + std["大於30Count"] + std["大於20Count"] + std["大於10Count"] + std["大於0Count"], class: 'poor-score' }
        ];

        // 找出我的成績在哪個區間
        const myRange = getMyScoreRange(getNumericScore(subject.ScoreDisplay, subject.Score));

        const card = document.createElement('div');
        card.className = 'distribution-card';
        card.innerHTML = `
            <h4>${subject.SubjectName}</h4>
            <div class="distribution-bars">
                ${ranges.map(r => {
            const percentage = (r.count / total) * 100;
            const isMine = r.label === myRange;
            return `
                        <div class="dist-row">
                            <span class="dist-label">${r.label}</span>
                            <div class="dist-bar-container">
                                <div class="dist-bar ${r.class}" style="width: ${Math.max(percentage, 5)}%">
                                    <span>${r.count}人</span>
                                </div>
                                ${isMine ? '<span class="my-score-marker">我</span>' : ''}
                            </div>
                        </div>
                    `;
        }).join('')}
            </div>
        `;
        grid.appendChild(card);
    });
}

function getMyScoreRange(score) {
    if (score >= 90) return '90-100';
    if (score >= 80) return '80-89';
    if (score >= 70) return '70-79';
    if (score >= 60) return '60-69';
    if (score >= 50) return '50-59';
    return '0-49';
}

function getNumericScore(displayValue, fallbackValue) {
    if (displayValue !== undefined && displayValue !== null && displayValue !== '') {
        const parsed = Number(displayValue);
        if (!Number.isNaN(parsed)) {
            return parsed;
        }
    }
    return Number(fallbackValue ?? 0);
}
