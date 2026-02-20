// ========================================
// 成績分析儀表板 - JavaScript
// ========================================

// 初始化
let radarChartInstance = null;
let barChartInstance = null;

document.addEventListener('DOMContentLoaded', () => {
    loadGradesData();
    setupFileImport();
    setupPasteImport();
    setupSyncFeature();
    setupShareFeature();
});

async function loadGradesData() {
    if (await checkSharedLink()) {
        return;
    }

    try {
        // ... rest of shared link checking logic is handled in checkSharedLink
        const storedData = getStoredGrades();
        if (storedData) {
            initDashboard(storedData);
            return;
        }

        // Default: No data loaded
        document.getElementById('updateTime').textContent = '無資料';
    } catch (error) {
        console.error(error);
        document.getElementById('updateTime').textContent = '無資料';
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
                const rawContent = normalizeFileContent(reader.result);
                const gradesData = JSON.parse(rawContent);
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

function setupPasteImport() {
    const importDropdown = document.querySelector('.import-dropdown');
    const importDropdownBtn = document.getElementById('importDropdownBtn');
    const pasteBtn = document.getElementById('pasteBtn');
    const pasteModal = document.getElementById('pasteModal');
    const closeModal = document.getElementById('closeModal');
    const cancelPaste = document.getElementById('cancelPaste');
    const confirmPaste = document.getElementById('confirmPaste');
    const jsonTextInput = document.getElementById('jsonTextInput');
    const gradesFile = document.getElementById('gradesFile');

    if (!importDropdownBtn || !importDropdown) return;

    // 下拉選單開關
    importDropdownBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        importDropdown.classList.toggle('active');
    });

    // 點擊外部關閉選單
    document.addEventListener('click', (e) => {
        if (!importDropdown.contains(e.target)) {
            importDropdown.classList.remove('active');
        }
    });

    // 選擇檔案後關閉選單
    if (gradesFile) {
        gradesFile.addEventListener('change', () => {
            importDropdown.classList.remove('active');
        });
    }

    if (!pasteBtn || !pasteModal) return;

    // 開啟彈出視窗
    pasteBtn.addEventListener('click', () => {
        importDropdown.classList.remove('active');
        pasteModal.classList.add('active');
        jsonTextInput.value = '';
        jsonTextInput.focus();
    });

    // 關閉彈出視窗的方式
    const closeModalFn = () => {
        pasteModal.classList.remove('active');
        jsonTextInput.value = '';
    };

    closeModal.addEventListener('click', closeModalFn);
    cancelPaste.addEventListener('click', closeModalFn);

    // 點擊背景關閉
    pasteModal.addEventListener('click', (e) => {
        if (e.target === pasteModal) {
            closeModalFn();
        }
    });

    // ESC 鍵關閉
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && pasteModal.classList.contains('active')) {
            closeModalFn();
        }
    });

    // 確認匯入
    confirmPaste.addEventListener('click', () => {
        const content = jsonTextInput.value.trim();
        if (!content) {
            alert('請輸入 JSON 內容');
            return;
        }

        try {
            const normalizedContent = normalizeFileContent(content);
            const gradesData = JSON.parse(normalizedContent);
            validateGradesData(gradesData);
            storeGradesData(gradesData);
            initDashboard(gradesData);
            closeModalFn();
        } catch (error) {
            console.error(error);
            alert(`匯入失敗：${error.message || 'JSON 格式不正確'}`);
        }
    });

    // 支援 Ctrl+V 直接貼上
    jsonTextInput.addEventListener('paste', () => {
        // 延遲一下讓內容先貼上
        setTimeout(() => {
            jsonTextInput.scrollTop = 0;
        }, 10);
    });
}

function normalizeFileContent(content) {
    if (content instanceof ArrayBuffer) {
        const decoder = new TextDecoder('utf-8');
        return decoder.decode(content).replace(/^\uFEFF/, '').trim();
    }

    if (typeof content === 'string') {
        return content.replace(/^\uFEFF/, '').trim();
    }

    return '';
}

function validateGradesData(gradesData) {
    if (!gradesData?.Result) {
        throw new Error('缺少 Result 資料');
    }

    const subjects = gradesData.Result.SubjectExamInfoList;
    if (!Array.isArray(subjects)) {
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

    // 更新排名資訊
    updateRankInfo(result);

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

// 更新排名資訊
function updateRankInfo(result) {
    const examItem = result.ExamItem;
    if (!examItem) return;

    // 班級排名
    const classRankBox = document.getElementById('classRankBox');
    const classRankEl = document.getElementById('classRank');
    if (result["Show班級排名"] && examItem.ClassRank !== null && examItem.ClassRank !== undefined) {
        const classRank = Math.floor(examItem.ClassRank);
        const classCount = examItem.ClassCount || 0;
        classRankEl.textContent = result["Show班級排名人數"] && classCount > 0
            ? `${classRank}/${classCount}`
            : `${classRank}`;
        classRankBox.style.display = 'block';
    } else {
        classRankBox.style.display = 'none';
    }

    // 類組排名
    const categoryRankBox = document.getElementById('categoryRankBox');
    const categoryRankEl = document.getElementById('categoryRank');
    if (result["Show類組排名"] && examItem["類組排名"] !== null && examItem["類組排名"] !== undefined) {
        const categoryRank = Math.floor(examItem["類組排名"]);
        const categoryCount = examItem["類組排名Count"] || 0;
        categoryRankEl.textContent = result["Show類組排名人數"] && categoryCount > 0
            ? `${categoryRank}/${categoryCount}`
            : `${categoryRank}`;
        categoryRankBox.style.display = 'block';
    } else {
        categoryRankBox.style.display = 'none';
    }
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

// 科目權重對照表
const SUBJECT_WEIGHTS = {
    '國語文': 4,
    '英語文': 4,
    '數學A': 4,
    '歷史': 2,
    '地理': 2,
    '公民與社會': 2,
    '選修化學': 2,
    '選修化學-物質與能量': 2,
    '選修物理': 2,
    '選修物理-力學一': 2
};

// 取得科目權重
function getSubjectWeight(subjectName) {
    // 先嘗試完全匹配
    if (SUBJECT_WEIGHTS[subjectName] !== undefined) {
        return SUBJECT_WEIGHTS[subjectName];
    }
    // 嘗試部分匹配（例如 "選修化學-物質與能量" 匹配 "選修化學"）
    for (const key of Object.keys(SUBJECT_WEIGHTS)) {
        if (subjectName.includes(key) || key.includes(subjectName)) {
            return SUBJECT_WEIGHTS[key];
        }
    }
    // 預設權重為 2
    return 2;
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
    const highest = Math.max(...scores);

    // 計算加權平均
    let totalWeightedScore = 0;
    let totalWeight = 0;

    subjects.forEach(subject => {
        const score = getNumericScore(subject.ScoreDisplay, subject.Score);
        const weight = getSubjectWeight(subject.SubjectName);
        totalWeightedScore += score * weight;
        totalWeight += weight;
    });

    const weightedAvg = totalWeight > 0 ? totalWeightedScore / totalWeight : 0;

    document.getElementById('avgScore').textContent = weightedAvg.toFixed(1);
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

        // 班排 (Class Rank)
        const hasClassRank = subject.ClassRank !== null && subject.ClassRank !== undefined;
        const classRank = hasClassRank ? Math.floor(subject.ClassRank) : null;
        const classRankCount = subject.ClassRankCount || 0;
        const classRankPercentile = hasClassRank && classRankCount > 0
            ? Math.round((1 - (classRank - 1) / classRankCount) * 100)
            : 0;

        // 校排 (Year/School Rank)
        const hasYearRank = subject.YearRank !== null && subject.YearRank !== undefined;
        const yearRank = hasYearRank ? Math.floor(subject.YearRank) : null;
        const yearRankCount = subject.YearRankCount || 0;

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
                ${hasClassRank || hasYearRank ? `
                <div class="rank-section">
                    ${hasClassRank ? `
                    <div class="rank-item">
                        <div class="rank-icon-wrapper class-rank-color">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                                <circle cx="9" cy="7" r="4"></circle>
                                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                            </svg>
                        </div>
                        <span class="rank-label">班排</span>
                        <span class="rank-value">
                            <span class="rank-number">${classRank}</span>${classRankCount > 0 ? `<span class="rank-total">/${classRankCount}</span>` : ''}
                        </span>
                        ${classRankCount > 0 ? `<span class="rank-percentile ${getRankPercentileClass(classRankPercentile)}">PR${classRankPercentile}</span>` : ''}
                    </div>
                    ` : ''}
                    ${hasYearRank ? `
                    <div class="rank-item">
                        <div class="rank-icon-wrapper year-rank-color">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M3 21h18"></path>
                                <path d="M5 21V7l8-4 8 4v14"></path>
                                <path d="M17 21v-8H7v8"></path>
                            </svg>
                        </div>
                        <span class="rank-label">校排</span>
                        <span class="rank-value">
                            <span class="rank-number">${yearRank}</span>${yearRankCount > 0 ? `<span class="rank-total">/${yearRankCount}</span>` : ''}
                        </span>
                    </div>
                    ` : ''}
                </div>
                ` : ''}
                <div class="progress-bar">
                    <div class="progress-fill" style="width: ${scoreValue}%"></div>
                </div>
            </div>
        `;
        grid.appendChild(card);
    });
}

// 取得排名百分位 CSS class
function getRankPercentileClass(percentile) {
    if (percentile >= 88) return 'pr-excellent';
    if (percentile >= 75) return 'pr-good';
    if (percentile >= 50) return 'pr-average';
    if (percentile >= 25) return 'pr-below';
    return 'pr-poor';
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
        '選修物理-力學一': '物理',
        '選修化學': '化學',
        '選修物理': '物理'
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
            <td>${shortenName(subject.SubjectName)}</td>
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
            const percentage = r.count === 0 ? 0 : (r.count / total) * 100;
            const isMine = r.label === myRange;
            const barClass = r.count === 0 ? '' : r.class;
            return `
                        <div class="dist-row">
                            <span class="dist-label">${r.label}</span>
                            <div class="dist-bar-container">
                                <div class="dist-bar ${barClass}" style="width: ${percentage === 0 ? 0 : Math.max(percentage, 5)}%"></div>
                                <span class="dist-count">${r.count}人</span>
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

// ========================================
// 同步功能邏輯
// ========================================
function setupSyncFeature() {
    const syncBtn = document.getElementById('syncBtn');

    // Modals
    const loginModal = document.getElementById('loginModal');
    const selectExamModal = document.getElementById('selectExamModal');

    // Login Form
    const closeLoginModal = document.getElementById('closeLoginModal');
    const cancelLogin = document.getElementById('cancelLogin');
    const confirmLogin = document.getElementById('confirmLogin');
    const usernameInput = document.getElementById('usernameInput');
    const passwordInput = document.getElementById('passwordInput');
    const loginStatus = document.getElementById('loginStatus');

    // Select Exam Form
    const closeSelectModal = document.getElementById('closeSelectModal');
    const cancelSelect = document.getElementById('cancelSelect');
    const confirmFetch = document.getElementById('confirmFetch');
    const yearSelect = document.getElementById('yearSelect');
    const examSelect = document.getElementById('examSelect');
    const fetchStatus = document.getElementById('fetchStatus');
    const logoutBtn = document.getElementById('logoutBtn');
    const reloadStructureBtn = document.getElementById('reloadStructureBtn');

    const API_BASE = '/api';
    let availableStructure = {}; // Store the loaded structure

    // Helper to toggle modal
    const toggleModal = (modal, show) => {
        if (show) modal.classList.add('active');
        else modal.classList.remove('active');
        // When closing select modal, clear interval
        if (modal === selectExamModal && !show && reloadInterval) {
            clearInterval(reloadInterval);
            reloadInterval = null;
        }
    };

    // Helper to show status
    const showStatus = (el, msg, type = 'normal') => {
        el.textContent = msg;
        el.className = `status-msg ${type}`;
    };

    // Cooldown Logic
    const COOLDOWN_MS = 60 * 1000;
    let reloadInterval = null;

    const updateReloadButtonState = () => {
        if (!reloadStructureBtn) return;

        const lastReload = parseInt(localStorage.getItem('lastReloadTime') || '0');
        const now = Date.now();
        const diff = now - lastReload;

        if (diff < COOLDOWN_MS) {
            const remain = Math.ceil((COOLDOWN_MS - diff) / 1000);
            reloadStructureBtn.disabled = true;
            reloadStructureBtn.textContent = `重載 (${remain}s)`;

            if (!reloadInterval) {
                reloadInterval = setInterval(updateReloadButtonState, 1000);
            }
        } else {
            reloadStructureBtn.disabled = false;
            reloadStructureBtn.textContent = '重載';
            if (reloadInterval) {
                clearInterval(reloadInterval);
                reloadInterval = null;
            }
        }
    };

    // Logout Logic
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            if (!confirm('確定要登出嗎？')) return;
            try {
                await fetch(`${API_BASE}/logout`, { method: 'POST' });
                location.reload();
            } catch (e) {
                alert('登出失敗');
            }
        });
    }

    // Reload Button Logic
    if (reloadStructureBtn) {
        reloadStructureBtn.addEventListener('click', () => {
            localStorage.setItem('lastReloadTime', Date.now());
            updateReloadButtonState(); // Update UI immediately
            openSelectExamModal(true);
        });
    }

    // 1. Click Sync Button (Optimistic UI)
    syncBtn.addEventListener('click', async () => {
        // Close dropdown if it exists
        const importDropdown = document.querySelector('.import-dropdown');
        if (importDropdown) {
            importDropdown.classList.remove('active');
        }

        openSelectExamModal();
    });

    // 2. Login Logic
    const handleLogin = async () => {
        const username = usernameInput.value.trim();
        const password = passwordInput.value.trim();

        if (!username || !password) {
            showStatus(loginStatus, '請輸入帳號密碼', 'error');
            return;
        }

        showStatus(loginStatus, '載入中...', 'normal');
        confirmLogin.disabled = true;

        try {
            const res = await fetch(`${API_BASE}/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ username, password })
            });
            const data = await res.json();

            if (data.success) {
                showStatus(loginStatus, '載入成功', 'success');
                setTimeout(() => {
                    toggleModal(loginModal, false);
                    openSelectExamModal();
                    passwordInput.value = '';
                    loginStatus.textContent = '';
                }, 500);
            } else {
                showStatus(loginStatus, data.message || '登入失敗', 'error');
            }
        } catch (error) {
            showStatus(loginStatus, '連線錯誤: ' + error.message, 'error');
        } finally {
            confirmLogin.disabled = false;
        }
    };

    confirmLogin.addEventListener('click', handleLogin);

    // Close Login Modal
    closeLoginModal.addEventListener('click', () => toggleModal(loginModal, false));
    cancelLogin.addEventListener('click', () => toggleModal(loginModal, false));

    // 3. Select Exam Logic
    const openSelectExamModal = async (forceReload = false) => {
        toggleModal(selectExamModal, true);

        // Update reload button state whenever modal opens
        updateReloadButtonState();

        yearSelect.innerHTML = '<option>載入中...</option>';
        examSelect.innerHTML = '<option>請先選擇學年度</option>';
        examSelect.disabled = true;
        confirmFetch.disabled = true;
        fetchStatus.textContent = '';
        availableStructure = {}; // Reset

        try {
            // Fetch ALL structure at once
            const url = forceReload ? `${API_BASE}/structure?reload=true` : `${API_BASE}/structure`;
            const res = await fetch(url, {
                credentials: 'include'
            });

            // Handle Unauthorized (401) -> Redirect to Login
            if (res.status === 401) {
                toggleModal(selectExamModal, false);
                toggleModal(loginModal, true);
                usernameInput.focus();
                return;
            }

            const data = await res.json();

            if (data.structure && Object.keys(data.structure).length > 0) {
                availableStructure = data.structure;

                yearSelect.innerHTML = '<option value="">請選擇學年度</option>';
                Object.keys(availableStructure).forEach(year => {
                    const opt = document.createElement('option');
                    opt.value = year;
                    opt.textContent = year;
                    yearSelect.appendChild(opt);
                });
            } else {
                yearSelect.innerHTML = '<option>無資料</option>';
            }
        } catch (error) {
            yearSelect.innerHTML = '<option>連線錯誤</option>';
        }
    };

    // Year change -> Load Exams from Local Structure
    yearSelect.addEventListener('change', () => {
        const year = yearSelect.value;
        if (!year) {
            examSelect.innerHTML = '<option>請先選擇學年度</option>';
            examSelect.disabled = true;
            return;
        }

        const yearData = availableStructure[year];
        const exams = yearData?.exams || [];

        if (exams.length > 0) {
            examSelect.innerHTML = '<option value="">請選擇考試</option>';
            exams.forEach(exam => {
                const opt = document.createElement('option');
                opt.value = exam.value;
                opt.textContent = exam.text;
                examSelect.appendChild(opt);
            });
            examSelect.disabled = false;
        } else {
            examSelect.innerHTML = '<option>無考試資料</option>';
            examSelect.disabled = true;
        }
        confirmFetch.disabled = true;
    });

    // Exam change -> Enable button
    examSelect.addEventListener('change', () => {
        confirmFetch.disabled = !examSelect.value;
    });

    // 4. Fetch Grades
    confirmFetch.addEventListener('click', async () => {
        const year = yearSelect.value;
        const exam = examSelect.value;

        if (!year || !exam) return;

        showStatus(fetchStatus, '正在抓取成績，請稍候...', 'normal');
        confirmFetch.disabled = true;

        try {
            const yearData = availableStructure[year];
            const res = await fetch(`${API_BASE}/fetch`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    year_value: yearData?.year_value,
                    exam_value: exam
                })
            });
            const data = await res.json();

            if (data.success) {
                showStatus(fetchStatus, '抓取成功！正在更新畫面...', 'success');
                // Update Dashboard
                validateGradesData(data.data);
                storeGradesData(data.data);
                initDashboard(data.data);

                setTimeout(() => {
                    toggleModal(selectExamModal, false);
                }, 1000);
            } else {
                showStatus(fetchStatus, data.error || '抓取失敗', 'error');
            }
        } catch (error) {
            showStatus(fetchStatus, '發生錯誤: ' + error.message, 'error');
        } finally {
            confirmFetch.disabled = false;
        }
    });

    // Close Select Modal
    closeSelectModal.addEventListener('click', () => toggleModal(selectExamModal, false));
    cancelSelect.addEventListener('click', () => toggleModal(selectExamModal, false));

    // Password Toggle Logic
    const togglePasswordBtn = document.getElementById('togglePasswordBtn');
    if (togglePasswordBtn && passwordInput) {
        togglePasswordBtn.addEventListener('click', () => {
            const type = passwordInput.getAttribute('type') === 'password' ? 'text' : 'password';
            passwordInput.setAttribute('type', type);

            // Toggle icons
            const eyeIcon = togglePasswordBtn.querySelector('.eye-icon');
            const eyeOffIcon = togglePasswordBtn.querySelector('.eye-off-icon');

            if (type === 'text') {
                eyeIcon.style.display = 'none';
                eyeOffIcon.style.display = 'block';
            } else {
                eyeIcon.style.display = 'block';
                eyeOffIcon.style.display = 'none';
            }
        });
    }
}

// ========================================
// 分享功能邏輯 (連結分享)
// ========================================
function setupShareFeature() {
    const shareBtn = document.getElementById('shareBtn');
    const shareModal = document.getElementById('shareModal');
    const closeShareModal = document.getElementById('closeShareModal');
    const sharePreview = document.getElementById('sharePreview');
    // Removed old buttons
    // New UI elements will be created or assumed present

    if (!shareBtn || !shareModal) return;

    // Reset Modal Content for Link Sharing
    const setupModalContent = () => {
        sharePreview.innerHTML = `
            <div style="text-align: center; padding: 8px 0;">
                <p style="color: var(--color-text-secondary); margin-bottom: 20px; font-size: 13px; line-height: 1.6;">
                    建立一個唯讀的分享連結，讓他人查看此成績單。<br>
                    連結將於 <strong style="color: var(--color-warning);">2 小時後</strong> 自動失效。
                </p>
                <div id="linkContainer" style="display: none; margin-bottom: 16px;">
                    <div class="form-group" style="margin-bottom: 12px;">
                        <input type="text" id="shareLinkInput" readonly
                            style="text-align: center; font-size: 13px; color: var(--color-primary); border-color: var(--color-primary); background: var(--color-primary-muted);">
                    </div>
                    <button id="copyLinkBtn" class="import-dropdown-btn" style="width: 100%; justify-content: center;">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                            fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                            stroke-linejoin="round">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                        </svg>
                        複製連結
                    </button>
                </div>
                <button id="createLinkBtn" class="import-dropdown-btn" style="width: 100%; justify-content: center;">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                        fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                        stroke-linejoin="round">
                        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
                        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
                    </svg>
                    建立分享連結
                </button>
                <div id="shareStatus" class="status-msg"></div>
            </div>
        `;

        const createLinkBtn = document.getElementById('createLinkBtn');
        const linkContainer = document.getElementById('linkContainer');
        const shareLinkInput = document.getElementById('shareLinkInput');
        const copyLinkBtn = document.getElementById('copyLinkBtn');
        const shareStatus = document.getElementById('shareStatus');

        createLinkBtn.addEventListener('click', async () => {
            const gradesData = getStoredGrades();
            if (!gradesData) {
                shareStatus.textContent = '無成績資料可分享';
                shareStatus.className = 'status-msg error';
                return;
            }

            createLinkBtn.disabled = true;
            createLinkBtn.textContent = '建立中...';
            shareStatus.textContent = '';

            try {
                const res = await fetch('/api/share', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(gradesData)
                });
                const data = await res.json();

                if (data.success) {
                    const link = `${window.location.origin}/share/${data.id}`;
                    shareLinkInput.value = link;
                    linkContainer.style.display = 'block';
                    createLinkBtn.style.display = 'none';
                    shareStatus.textContent = '連結建立成功！';
                    shareStatus.className = 'status-msg success';
                } else {
                    throw new Error(data.error || '建立失敗');
                }
            } catch (error) {
                console.error(error);
                shareStatus.textContent = '建立失敗: ' + error.message;
                shareStatus.className = 'status-msg error';
                createLinkBtn.disabled = false;
                createLinkBtn.textContent = '建立分享連結';
            }
        });

        copyLinkBtn.addEventListener('click', () => {
            shareLinkInput.select();
            navigator.clipboard.writeText(shareLinkInput.value).then(() => {
                const originalText = copyLinkBtn.textContent;
                copyLinkBtn.textContent = '已複製！';
                setTimeout(() => {
                    copyLinkBtn.textContent = originalText;
                }, 2000);
            });
        });
    };

    // Open Modal
    shareBtn.addEventListener('click', () => {
        shareModal.classList.add('active');
        setupModalContent();
    });

    // Close Modal
    const closeModalFn = () => {
        shareModal.classList.remove('active');
    };
    closeShareModal.addEventListener('click', closeModalFn);
}

// Check for shared link on load
async function checkSharedLink() {
    const path = window.location.pathname;
    const match = path.match(/^\/share\/([A-Za-z0-9\-_.~]+)$/);

    if (match) {
        const shareId = match[1];
        console.log("Viewing shared grade:", shareId);

        // Enter Read-Only Mode
        document.body.classList.add('read-only-mode');

        // Hide interactive elements
        const elementsToHide = [
            '#shareBtn',
            '.import-dropdown', // Create/Import buttons
            '.data-time-box .import-dropdown'
        ];

        // Wait for DOM to be ready just in case, though this is called in loadGradesData context usually
        // But better to hide immediately via CSS or here

        // Show loading state
        const updateTime = document.getElementById('updateTime');
        if (updateTime) updateTime.textContent = '載入分享資料中...';

        try {
            const res = await fetch(`/api/share/${shareId}`);
            if (res.status === 404) {
                alert('此分享連結已過期或不存在。');
                window.location.href = '/';
                return;
            }

            const data = await res.json();
            if (data.success) {
                initDashboard(data.data);

                // Add header indicator
                const header = document.querySelector('.header-content');
                const indicator = document.createElement('div');
                indicator.className = 'share-indicator';
                indicator.innerHTML = '<span style="color: var(--color-warning); font-weight: bold; padding: 4px 8px; border: 1px solid var(--color-warning); border-radius: 4px; font-size: 12px;">僅供檢視 (唯讀)</span>';
                // Insert before data-time-box
                const timeBox = document.querySelector('.data-time-box');
                header.insertBefore(indicator, timeBox);

                if (updateTime) updateTime.textContent = '分享存檔';
            } else {
                throw new Error(data.error);
            }
        } catch (error) {
            console.error(error);
            alert('載入分享資料失敗: ' + error.message);
            window.location.href = '/';
        }
        return true; // Handled shared link
    }
    return false;
}
