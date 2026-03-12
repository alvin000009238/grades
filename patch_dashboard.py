with open('frontend/dashboard.js', 'r') as f:
    content = f.read()

# Function 1: generateScoreCards
content = content.replace(
    """function generateScoreCards(subjects) {
    const grid = document.getElementById('scoresGrid');
    grid.innerHTML = '';

    subjects.forEach(subject => {""",
    """function generateScoreCards(subjects) {
    const grid = document.getElementById('scoresGrid');
    grid.innerHTML = '';

    // Create a DocumentFragment to batch DOM insertions and reduce reflows
    const fragment = document.createDocumentFragment();

    subjects.forEach(subject => {"""
)

content = content.replace(
    """        `;
        grid.appendChild(card);
    });
}""",
    """        `;
        fragment.appendChild(card);
    });

    // Append all cards at once
    grid.appendChild(fragment);
}""",
    1 # Replace first match (generateScoreCards)
)

# Function 2: generateStandardsTable
content = content.replace(
    """function generateStandardsTable(subjects, standards) {
    const tbody = document.getElementById('standardsBody');
    tbody.innerHTML = '';

    subjects.forEach((subject, index) => {""",
    """function generateStandardsTable(subjects, standards) {
    const tbody = document.getElementById('standardsBody');
    tbody.innerHTML = '';

    // Create a DocumentFragment to batch DOM insertions and reduce reflows
    const fragment = document.createDocumentFragment();

    subjects.forEach((subject, index) => {"""
)

content = content.replace(
    """        `;
        tbody.appendChild(row);
    });
}""",
    """        `;
        fragment.appendChild(row);
    });

    // Append all rows at once
    tbody.appendChild(fragment);
}"""
)

# Function 3: generateDistributionCards
content = content.replace(
    """function generateDistributionCards(subjects, standards) {
    const grid = document.getElementById('distributionGrid');
    grid.innerHTML = '';

    subjects.forEach((subject, index) => {""",
    """function generateDistributionCards(subjects, standards) {
    const grid = document.getElementById('distributionGrid');
    grid.innerHTML = '';

    // Create a DocumentFragment to batch DOM insertions and reduce reflows
    const fragment = document.createDocumentFragment();

    subjects.forEach((subject, index) => {"""
)

content = content.replace(
    """        `;
        grid.appendChild(card);
    });
}""",
    """        `;
        fragment.appendChild(card);
    });

    // Append all distribution cards at once
    grid.appendChild(fragment);
}"""
)

with open('frontend/dashboard.js', 'w') as f:
    f.write(content)
