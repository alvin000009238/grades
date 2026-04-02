// ========================================
// 儀表板渲染與統計
// ========================================

import { escapeHTML } from './storage.js';
import { generateCharts } from './charts.js';

// 科目權重對照表
const SUBJECT_WEIGHTS = {
    '國語文': 4,
    '英語文': 4,
    '數學': 4,
};

// 預先計算鍵值，避免在熱點函數中重複建立陣列，提升效能並減少 GC 開銷
const SUBJECT_WEIGHT_KEYS = Object.keys(SUBJECT_WEIGHTS);

// 取得科目權重
function getSubjectWeight(subjectName) {
    // 先嘗試完全匹配
    if (SUBJECT_WEIGHTS[subjectName] !== undefined) {
        return SUBJECT_WEIGHTS[subjectName];
    }
    // 嘗試部分匹配（例如 "選修化學-物質與能量" 匹配 "選修化學"）
    for (const key of SUBJECT_WEIGHT_KEYS) {
        if (subjectName.includes(key) || key.includes(subjectName)) {
            return SUBJECT_WEIGHTS[key];
        }
    }
    // 預設權重為 2
    return 2;
}

export function initDashboard(gradesData) {
    const result = gradesData.Result;
    const standards = Array.isArray(result["成績五標List"]) ? result["成績五標List"] : [];
    const subjects = result.SubjectExamInfoList || [];
    const preparedSubjects = prepareSubjects(subjects);
    const standardsLookup = createStandardsLookup(standards);

    // Phase 1: 優先渲染基本文字資訊與統計，即時反饋給使用者
    requestAnimationFrame(() => {
        updateStudentInfo(result);
        updateExamInfo(result);
        updateRankInfo(result);
        updateStatistics(preparedSubjects);

        // Phase 2: 處理成績卡片的 DOM 生成
        requestAnimationFrame(() => {
            generateScoreCards(preparedSubjects);

            // Phase 3: 處理五標表格與圖表加載 (可能涉及較多運算與外部腳本)
            requestAnimationFrame(() => {
                generateStandardsTable(preparedSubjects, standards, standardsLookup);
                generateCharts(preparedSubjects);

                // Phase 4: 最後生成可能在畫面最下方的分佈圖，改在閒置時間執行避免阻塞首屏
                scheduleLowPriorityRender(() => {
                    generateDistributionCards(preparedSubjects, standards, standardsLookup);
                });
            });
        });
    });
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

// 計算統計
function updateStatistics(subjects) {
    if (!subjects.length) {
        document.getElementById('avgScore').textContent = '--';
        document.getElementById('totalSubjects').textContent = '0';
        document.getElementById('highestScore').textContent = '--';
        return;
    }

    let highest = -Infinity;
    let totalWeightedScore = 0;
    let totalWeight = 0;

    // Bolt: Combine O(n) iterations to eliminate intermediate array and Math.max
    for (let i = 0; i < subjects.length; i++) {
        const subject = subjects[i];
        const score = subject.scoreValue;

        if (score > highest) highest = score;

        const weight = getSubjectWeight(subject.SubjectName);
        totalWeightedScore += score * weight;
        totalWeight += weight;
    }

    const weightedAvg = totalWeight > 0 ? totalWeightedScore / totalWeight : 0;

    document.getElementById('avgScore').textContent = weightedAvg.toFixed(1);
    document.getElementById('totalSubjects').textContent = subjects.length;
    document.getElementById('highestScore').textContent = highest;
}

// 生成成績卡片
function generateScoreCards(subjects) {
    const grid = document.getElementById('scoresGrid');
    grid.innerHTML = '';

    // Create a DocumentFragment to batch DOM insertions and reduce reflows
    const fragment = document.createDocumentFragment();

    subjects.forEach(subject => {
        const scoreValue = subject.scoreValue;
        const classAvgValue = subject.classAvgValue;
        const diff = subject.diffValue;
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
                <span class="subject-name">${escapeHTML(subject.SubjectName)}</span>
                <span class="score-badge ${scoreClass}">${escapeHTML(subject.ScoreDisplay ?? scoreValue)}</span>
            </div>
            <div class="score-details">
                <div class="score-row">
                    <span class="score-label">班級平均</span>
                    <span class="score-value">${escapeHTML(subject.ClassAVGScoreDisplay ?? classAvgValue.toFixed(2))}</span>
                </div>
                <div class="score-row">
                    <span class="score-label">與班平均差距</span>
                    <span class="diff-indicator ${diffClass}">
                        ${diffIcon} ${escapeHTML(Math.abs(diff).toFixed(2))}
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
                            <span class="rank-number">${escapeHTML(classRank)}</span>${classRankCount > 0 ? `<span class="rank-total">/${escapeHTML(classRankCount)}</span>` : ''}
                        </span>
                        ${classRankCount > 0 ? `<span class="rank-percentile ${getRankPercentileClass(classRankPercentile)}">PR${escapeHTML(classRankPercentile)}</span>` : ''}
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
                            <span class="rank-number">${escapeHTML(yearRank)}</span>${yearRankCount > 0 ? `<span class="rank-total">/${escapeHTML(yearRankCount)}</span>` : ''}
                        </span>
                    </div>
                    ` : ''}
                </div>
                ` : ''}
                <div class="progress-bar">
                    <div class="progress-fill" data-width="${scoreValue}"></div>
                </div>
            </div>
        `;
        fragment.appendChild(card);
    });

    // Append all cards at once
    grid.appendChild(fragment);

    // 配合 CSP: 'unsafe-inline' 去除後無法使用 style="..." 的問題
    grid.querySelectorAll('.progress-fill').forEach(el => {
        const w = el.getAttribute('data-width');
        if (w !== null) el.style.width = w + '%';
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

// 縮短科目名稱（僅保留 '-' 前的文字）
export function shortenName(name = '') {
    const cleanedName = cleanSubjectName(String(name));
    const [baseName] = cleanedName.split('-');
    return baseName.trim() || cleanedName;
}

// 生成五標表格
function generateStandardsTable(subjects, standards, standardsLookup) {
    const tbody = document.getElementById('standardsBody');
    tbody.innerHTML = '';

    // Create a DocumentFragment to batch DOM insertions and reduce reflows
    const fragment = document.createDocumentFragment();

    subjects.forEach((subject, index) => {
        const std = standardsLookup.get(subject.SubjectName) || standards[index];
        if (!std) return;

        const score = subject.scoreValue;
        const level = getScoreLevel(score, std);

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHTML(shortenName(subject.SubjectName))}</td>
            <td class="top-mark">${escapeHTML(std["頂標"].toFixed(2))}</td>
            <td class="front-mark">${escapeHTML(std["前標"].toFixed(2))}</td>
            <td class="avg-mark">${escapeHTML(std["均標"].toFixed(2))}</td>
            <td class="back-mark">${escapeHTML(std["後標"].toFixed(2))}</td>
            <td class="bottom-mark">${escapeHTML(std["底標"].toFixed(2))}</td>
            <td>${escapeHTML(std["標準差"].toFixed(2))}</td>
            <td><span class="my-score ${level.class}">${escapeHTML(score)}</span></td>
            <td><span class="level-badge ${level.class}">${escapeHTML(level.text)}</span></td>
        `;
        fragment.appendChild(row);
    });

    // Append all rows at once
    tbody.appendChild(fragment);
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
function generateDistributionCards(subjects, standards, standardsLookup) {
    const grid = document.getElementById('distributionGrid');
    grid.innerHTML = '';

    // Create a DocumentFragment to batch DOM insertions and reduce reflows
    const fragment = document.createDocumentFragment();

    subjects.forEach((subject, index) => {
        const std = standardsLookup.get(subject.SubjectName) || standards[index];
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
        const myRange = getMyScoreRange(subject.scoreValue);

        const card = document.createElement('div');
        card.className = 'distribution-card';
        card.innerHTML = `
            <h4>${escapeHTML(subject.SubjectName)}</h4>
            <div class="distribution-bars">
                ${ranges.map(r => {
            const percentage = r.count === 0 ? 0 : (r.count / total) * 100;
            const isMine = r.label === myRange;
            const barClass = r.count === 0 ? '' : r.class;
            return `
                        <div class="dist-row">
                            <span class="dist-label">${escapeHTML(r.label)}</span>
                            <div class="dist-bar-container">
                                <div class="dist-bar ${barClass}" data-width="${percentage === 0 ? 0 : Math.max(percentage, 5)}"></div>
                                <span class="dist-count">${escapeHTML(r.count)}人</span>
                                ${isMine ? '<span class="my-score-marker">我</span>' : ''}
                            </div>
                        </div>
                    `;
        }).join('')}
            </div>
        `;
        fragment.appendChild(card);
    });

    // Append all distribution cards at once
    grid.appendChild(fragment);

    // 配合 CSP: 'unsafe-inline' 去除後無法使用 style="..." 的問題
    grid.querySelectorAll('.dist-bar').forEach(el => {
        const w = el.getAttribute('data-width');
        if (w !== null) el.style.width = w + '%';
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

function prepareSubjects(subjects) {
    return subjects.map(subject => {
        const scoreValue = getNumericScore(subject.ScoreDisplay, subject.Score);
        const classAvgValue = getNumericScore(subject.ClassAVGScoreDisplay, subject.ClassAVGScore);
        return {
            ...subject,
            scoreValue,
            classAvgValue,
            diffValue: scoreValue - classAvgValue
        };
    });
}

function createStandardsLookup(standards) {
    const lookup = new Map();
    standards.forEach(std => {
        lookup.set(cleanSubjectName(std.SubjectName), std);
    });
    return lookup;
}

function scheduleLowPriorityRender(renderFn) {
    if (typeof window.requestIdleCallback === 'function') {
        window.requestIdleCallback(renderFn, { timeout: 1000 });
        return;
    }
    setTimeout(renderFn, 0);
}

export function getNumericScore(displayValue, fallbackValue) {
    if (displayValue !== undefined && displayValue !== null && displayValue !== '') {
        const parsed = Number(displayValue);
        if (!Number.isNaN(parsed)) {
            return parsed;
        }
    }
    return Number(fallbackValue ?? 0);
}
