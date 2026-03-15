import {
    DEMO_CREDENTIALS,
    DEMO_DEFAULT_EXAM_VALUE,
    DEMO_DEFAULT_YEAR,
    DEMO_STRUCTURE,
    getDemoGradesData
} from './demo-data.js';

let demoModeEnabled = false;

export function setDemoModeEnabled(enabled) {
    demoModeEnabled = Boolean(enabled);
}

export function isDemoModeEnabled() {
    return demoModeEnabled;
}

export function getDemoCredentials() {
    return { ...DEMO_CREDENTIALS };
}

export function getDemoStructure() {
    return JSON.parse(JSON.stringify(DEMO_STRUCTURE));
}

export function getDemoDefaults() {
    return {
        year: DEMO_DEFAULT_YEAR,
        exam: DEMO_DEFAULT_EXAM_VALUE
    };
}

export function getDemoResultData() {
    return getDemoGradesData();
}
