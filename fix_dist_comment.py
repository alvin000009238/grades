with open('frontend/dashboard.js', 'r') as f:
    content = f.read()

content = content.replace(
    """    // Append all cards at once
    grid.appendChild(fragment);
}

function getMyScoreRange(score) {""",
    """    // Append all distribution cards at once
    grid.appendChild(fragment);
}

function getMyScoreRange(score) {"""
)

with open('frontend/dashboard.js', 'w') as f:
    f.write(content)
