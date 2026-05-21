package com.ideaminer.model;

public class ClassMetadata {
    private String id;
    private String repoName;
    private String className;
    private String packageName;
    private String filePath;
    private String classType;
    private String summary;
    private int cyclomaticComplexity;
    private String sourceCode;

    public ClassMetadata() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }
    
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public String getClassType() { return classType; }
    public void setClassType(String classType) { this.classType = classType; }
    
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    
    public int getCyclomaticComplexity() { return cyclomaticComplexity; }
    public void setCyclomaticComplexity(int cyclomaticComplexity) { this.cyclomaticComplexity = cyclomaticComplexity; }
    
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    
    @Override
    public String toString() {
        return "ClassMetadata{class=" + className + ", type=" + classType + "}";
    }
}
