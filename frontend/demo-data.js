export const DEMO_CREDENTIALS = {
    username: 'demo.student',
    password: 'demo.password'
};

export const DEMO_DEFAULT_YEAR = '114 學年度 上學期';
export const DEMO_DEFAULT_EXAM_VALUE = 'demo_exam_1';
export const DEMO_DEFAULT_EXAM_TEXT = '第一次段考（教學）';

export const DEMO_STRUCTURE = {
    [DEMO_DEFAULT_YEAR]: {
        year_value: 'demo_114_1',
        exams: [
            {
                value: DEMO_DEFAULT_EXAM_VALUE,
                text: DEMO_DEFAULT_EXAM_TEXT
            }
        ]
    }
};

const DEMO_SUBJECTS = [
    { name: '國語文', score: 84, classAvg: 72.3, classRank: 5, yearRank: 45 },
    { name: '英語文', score: 79, classAvg: 70.8, classRank: 9, yearRank: 69 },
    { name: '數學A', score: 68, classAvg: 66.2, classRank: 14, yearRank: 110 },
    { name: '歷史', score: 88, classAvg: 73.1, classRank: 3, yearRank: 30 },
    { name: '地理', score: 82, classAvg: 71.5, classRank: 6, yearRank: 56 },
    { name: '公民與社會', score: 76, classAvg: 69.0, classRank: 10, yearRank: 77 },
    { name: '選修化學-物質與能量', score: 73, classAvg: 67.6, classRank: 11, yearRank: 89 },
    { name: '選修物理-力學一', score: 69, classAvg: 65.9, classRank: 13, yearRank: 103 }
];

const DEMO_STANDARDS = [
    { subject: '國語文', top: 88, front: 80, avg: 72, back: 63, bottom: 56, std: 9.2, c90: 3, c80: 6, c70: 12, c60: 10, c50: 3, c40: 2, c30: 0, c20: 0, c10: 0, c0: 0 },
    { subject: '英語文', top: 86, front: 78, avg: 71, back: 62, bottom: 55, std: 10.1, c90: 2, c80: 6, c70: 11, c60: 11, c50: 4, c40: 2, c30: 0, c20: 0, c10: 0, c0: 0 },
    { subject: '數學A', top: 84, front: 75, avg: 66, back: 56, bottom: 48, std: 12.4, c90: 1, c80: 4, c70: 9, c60: 11, c50: 6, c40: 4, c30: 1, c20: 0, c10: 0, c0: 0 },
    { subject: '歷史', top: 89, front: 81, avg: 73, back: 64, bottom: 57, std: 8.8, c90: 3, c80: 7, c70: 11, c60: 9, c50: 4, c40: 2, c30: 0, c20: 0, c10: 0, c0: 0 },
    { subject: '地理', top: 87, front: 79, avg: 71, back: 62, bottom: 54, std: 9.4, c90: 2, c80: 7, c70: 10, c60: 10, c50: 5, c40: 2, c30: 0, c20: 0, c10: 0, c0: 0 },
    { subject: '公民與社會', top: 85, front: 77, avg: 69, back: 60, bottom: 52, std: 10.3, c90: 2, c80: 5, c70: 10, c60: 11, c50: 6, c40: 2, c30: 0, c20: 0, c10: 0, c0: 0 },
    { subject: '選修化學-物質與能量', top: 83, front: 75, avg: 68, back: 59, bottom: 51, std: 11.2, c90: 1, c80: 4, c70: 10, c60: 11, c50: 7, c40: 2, c30: 1, c20: 0, c10: 0, c0: 0 },
    { subject: '選修物理-力學一', top: 82, front: 74, avg: 66, back: 57, bottom: 49, std: 11.7, c90: 1, c80: 4, c70: 9, c60: 12, c50: 6, c40: 3, c30: 1, c20: 0, c10: 0, c0: 0 }
];

const SUBJECT_COUNT = 36;
const YEAR_COUNT = 360;

const DEMO_GRADES_DATA = {
    Message: '',
    Status: 'Success',
    Result: {
        StudentNo: 'D114001',
        StudentName: '教學同學',
        StudentClassName: '高二 1 班',
        StudentSeatNo: '12',
        GetDataTimeDisplay: '2026/03/15 20:00',
        Show班級排名: true,
        Show班級排名人數: true,
        Show類組排名: true,
        Show類組排名人數: true,
        ExamItem: {
            ExamName: DEMO_DEFAULT_EXAM_TEXT,
            ClassRank: 8,
            ClassCount: SUBJECT_COUNT,
            類組排名: 28,
            類組排名Count: 220
        },
        SubjectExamInfoList: DEMO_SUBJECTS.map((item) => ({
            SubjectName: item.name,
            ScoreDisplay: item.score.toFixed(2),
            Score: item.score,
            ClassAVGScoreDisplay: item.classAvg.toFixed(2),
            ClassAVGScore: item.classAvg,
            ClassRank: item.classRank,
            ClassRankCount: SUBJECT_COUNT,
            YearRank: item.yearRank,
            YearRankCount: YEAR_COUNT,
            YearTermDisplay: DEMO_DEFAULT_YEAR
        })),
        成績五標List: DEMO_STANDARDS.map((item) => ({
            SubjectName: item.subject,
            頂標: item.top,
            前標: item.front,
            均標: item.avg,
            後標: item.back,
            底標: item.bottom,
            標準差: item.std,
            大於90Count: item.c90,
            大於80Count: item.c80,
            大於70Count: item.c70,
            大於60Count: item.c60,
            大於50Count: item.c50,
            大於40Count: item.c40,
            大於30Count: item.c30,
            大於20Count: item.c20,
            大於10Count: item.c10,
            大於0Count: item.c0
        }))
    }
};

export function getDemoGradesData() {
    return JSON.parse(JSON.stringify(DEMO_GRADES_DATA));
}
