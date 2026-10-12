@REM Apache Maven Wrapper startup script for Windows
@SETLOCAL
SET MAVEN_PROJECTBASEDIR=%~dp0
SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
SET WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"

IF NOT EXIST %WRAPPER_JAR% (
  echo Downloading Maven Wrapper...
  powershell -Command "(New-Object Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar', '%WRAPPER_JAR%')"
)

IF "%JAVA_HOME%"=="" (
  SET JAVACMD=java
) ELSE (
  SET JAVACMD="%JAVA_HOME%\bin\java"
)

%JAVACMD% -cp %WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
ENDLOCAL
