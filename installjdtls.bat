cd %~dp0
bitsadmin.exe /transfer "downloadjdtls" /download /priority FOREGROUND http://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz "%cd%/jdt-language-server-latest.tar.gz"
if exist jdtls (
    rd /s /q jdtls
)
mkdir jdtls
tar -xzf jdt-language-server-latest.tar.gz -C jdtls/
set workspaceRoot=%~dp0
cd jdtls
for /R ./plugins %%F in (org.eclipse.jdt.ls.core_*.jar) do (
    echo %%F
    %workspaceRoot%/mvnw.cmd install:install-file -Dfile="%%F" -DgroupId=org.eclipse.jdt.ls -DartifactId=org.eclipse.jdt.ls.core -Dversion=0.4.0 -Dpackaging=jar
)
cd %workspaceRoot%
