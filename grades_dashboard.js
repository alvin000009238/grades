// ========================================
// 成績分析儀表板 - JavaScript
// ========================================

// 成績資料
const gradesData = {
    "Message": "",
    "Result": {
        "StudentNo": "310471",
        "StudentName": "高浚瑋",
        "StudentClassNo": "211",
        "StudentClassName": "二年 11 班",
        "StudentSeatNo": "20",
        "StudentId": "37083",
        "ExamItem": {
            "ExamName": "期末考_1"
        },
        "SubjectExamInfoList": [
            {
                "SubjectName": "英語文",
                "Score": 64.0,
                "ClassAVGScore": 62.76
            },
            {
                "SubjectName": "歷史",
                "Score": 70.0,
                "ClassAVGScore": 69.16
            },
            {
                "SubjectName": "地理",
                "Score": 70.0,
                "ClassAVGScore": 68.97
            },
            {
                "SubjectName": "公民與社會",
                "Score": 80.0,
                "ClassAVGScore": 65.68
            },
            {
                "SubjectName": "數學A",
                "Score": 54.0,
                "ClassAVGScore": 65.41
            },
            {
                "SubjectName": "選修化學-物質與能量",
                "Score": 69.0,
                "ClassAVGScore": 75.43
            },
            {
                "SubjectName": "選修物理-力學一",
                "Score": 69.0,
                "ClassAVGScore": 70.03
            }
        ],
        "GetDataTimeDisplay": "2026/01/17 15:44",
        "成績五標List": [
            {
                "SubjectName": "英語文",
                "頂標": 82.44,
                "前標": 74.72,
                "均標": 62.76,
                "後標": 50.72,
                "底標": 43.22,
                "標準差": 15.29,
                "大於90Count": 2,
                "大於80Count": 2,
                "大於70Count": 5,
                "大於60Count": 14,
                "大於50Count": 7,
                "大於40Count": 5,
                "大於30Count": 1,
                "大於20Count": 1,
                "大於10Count": 0,
                "大於0Count": 0
            },
            {
                "SubjectName": "歷史",
                "頂標": 82.11,
                "前標": 77.0,
                "均標": 69.16,
                "後標": 61.28,
                "底標": 56.89,
                "標準差": 9.82,
                "大於90Count": 1,
                "大於80Count": 5,
                "大於70Count": 13,
                "大於60Count": 12,
                "大於50Count": 6,
                "大於40Count": 0,
                "大於30Count": 0,
                "大於20Count": 0,
                "大於10Count": 0,
                "大於0Count": 0
            },
            {
                "SubjectName": "地理",
                "頂標": 81.33,
                "前標": 76.22,
                "均標": 68.97,
                "後標": 61.78,
                "底標": 58.67,
                "標準差": 9.04,
                "大於90Count": 1,
                "大於80Count": 3,
                "大於70Count": 13,
                "大於60Count": 16,
                "大於50Count": 4,
                "大於40Count": 0,
                "大於30Count": 0,
                "大於20Count": 0,
                "大於10Count": 0,
                "大於0Count": 0
            },
            {
                "SubjectName": "公民與社會",
                "頂標": 78.44,
                "前標": 73.44,
                "均標": 65.68,
                "後標": 57.89,
                "底標": 52.78,
                "標準差": 10.14,
                "大於90Count": 0,
                "大於80Count": 3,
                "大於70Count": 8,
                "大於60Count": 18,
                "大於50Count": 7,
                "大於40Count": 0,
                "大於30Count": 1,
                "大於20Count": 0,
                "大於10Count": 0,
                "大於0Count": 0
            },
            {
                "SubjectName": "數學A",
                "頂標": 86.67,
                "前標": 78.67,
                "均標": 65.41,
                "後標": 52.33,
                "底標": 47.56,
                "標準差": 15.39,
                "大於90Count": 3,
                "大於80Count": 6,
                "大於70Count": 5,
                "大於60Count": 7,
                "大於50Count": 13,
                "大於40Count": 1,
                "大於30Count": 2,
                "大於20Count": 0,
                "大於10Count": 0,
                "大於0Count": 0
            },
            {
                "SubjectName": "選修化學-物質與能量",
                "頂標": 93.44,
                "前標": 88.56,
                "均標": 75.43,
                "後標": 62.17,
                "底標": 52.78,
                "標準差": 17.24,
                "大於90Count": 8,
                "大於80Count": 9,
                "大於70Count": 7,
                "大於60Count": 8,
                "大於50Count": 3,
                "大於40Count": 0,
                "大於30Count": 1,
                "大於20Count": 0,
                "大於10Count": 1,
                "大於0Count": 0
            },
            {
                "SubjectName": "選修物理-力學一",
                "頂標": 91.22,
                "前標": 84.22,
                "均標": 70.03,
                "後標": 55.83,
                "底標": 48.22,
                "標準差": 16.87,
                "大於90Count": 6,
                "大於80Count": 5,
                "大於70Count": 8,
                "大於60Count": 6,
                "大於50Count": 8,
                "大於40Count": 3,
                "大於30Count": 0,
                "大於20Count": 1,
                "大於10Count": 0,
                "大於0Count": 0
            }
        ]
    },
    "Status": "Success"
};

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    initDashboard();
});

function initDashboard() {
    const result = gradesData.Result;

    // 更新學生資訊
    updateStudentInfo(result);

    // 計算統計資料
    updateStatistics(result.SubjectExamInfoList);

    // 生成成績卡片
    generateScoreCards(result.SubjectExamInfoList);

    // 生成圖表
    generateCharts(result.SubjectExamInfoList);

    // 生成五標表格
    generateStandardsTable(result.SubjectExamInfoList, result["成績五標List"]);

    // 生成分佈圖
    generateDistributionCards(result.SubjectExamInfoList, result["成績五標List"]);
}

// 更新學生資訊
function updateStudentInfo(result) {
    document.getElementById('studentName').textContent = result.StudentName;
    document.getElementById('studentClass').textContent = result.StudentClassName;
    document.getElementById('studentSeat').textContent = result.StudentSeatNo;
    document.getElementById('studentNo').textContent = result.StudentNo;
    document.getElementById('avatarText').textContent = result.StudentName.charAt(0);
    document.getElementById('updateTime').textContent = result.GetDataTimeDisplay;
}

// 計算統計
function updateStatistics(subjects) {
    const scores = subjects.map(s => s.Score);
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
        const diff = subject.Score - subject.ClassAVGScore;
        const scoreClass = getScoreClass(subject.Score);
        const diffClass = diff >= 0 ? 'positive' : 'negative';
        const diffIcon = diff >= 0 ? '↑' : '↓';

        const card = document.createElement('div');
        card.className = 'score-card';
        card.innerHTML = `
            <div class="score-header">
                <span class="subject-name">${subject.SubjectName}</span>
                <span class="score-badge ${scoreClass}">${subject.Score}</span>
            </div>
            <div class="score-details">
                <div class="score-row">
                    <span class="score-label">班級平均</span>
                    <span class="score-value">${subject.ClassAVGScore.toFixed(2)}</span>
                </div>
                <div class="score-row">
                    <span class="score-label">與班平均差距</span>
                    <span class="diff-indicator ${diffClass}">
                        ${diffIcon} ${Math.abs(diff).toFixed(2)}
                    </span>
                </div>
                <div class="progress-bar">
                    <div class="progress-fill" style="width: ${subject.Score}%"></div>
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
    const myScores = subjects.map(s => s.Score);
    const avgScores = subjects.map(s => s.ClassAVGScore);

    // 雷達圖
    const radarCtx = document.getElementById('radarChart').getContext('2d');
    new Chart(radarCtx, {
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
    new Chart(barCtx, {
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

        const score = subject.Score;
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
        const myRange = getMyScoreRange(subject.Score);

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
