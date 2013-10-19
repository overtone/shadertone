Here are the steps that I followed to create the https://clojars.org/shadertone/lwjgl-natives files.

1) Create jar/pom

http://nakkaya.com/2010/04/05/managing-native-dependencies-with-leiningen/

mkdir -p native/macosx/x86
mkdir -p native/macosx/x86_64
mkdir -p native/linux/x86
mkdir -p native/linux/x86_64
mkdir -p native/windows/x86
mkdir -p native/windows/x86_64

cp ~/.m2/repository/org/lwjgl/lwjgl/lwjgl-platform/2.9.0/*jar .
jar xvf lwjgl-platform-2.9.0-natives-linux.jar
jar xvf lwjgl-platform-2.9.0-natives-osx.jar
jar xvf lwjgl-platform-2.9.0-natives-windows.jar

cp liblwjgl.jnilib native/macosx/x86
mv liblwjgl.jnilib native/macosx/x86_64/
cp openal.dylib    native/macosx/x86_64/
mv openal.dylib    native/macosx/x86
mv liblwjgl.so     native/linux/x86
mv libopenal.so    native/linux/x86
mv liblwjgl64.so   native/linux/x86_64
mv libopenal64.so  native/linux/x86_64
mv OpenAL32.dll    native/windows/x86
mv lwjgl.dll       native/windows/x86
mv OpenAL64.dll    native/windows/x86_64
mv lwjgl64.dll     native/windows/x86_64

rm lwjgl-natives-2.9.0.jar
jar -cMf lwjgl-natives-2.9.0.jar native

2) edit pom.xml

<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>shadertone</groupId>
  <artifactId>lwjgl-natives</artifactId>
  <version>2.9.0</version>
  <name>lwjgl-natives</name>
  <description>shadertone LWJGL native libs</description>
  <url>http://github.com/overtone/shadertone</url>
</project>


3) Local test install

http://maven.apache.org/plugins/maven-install-plugin/examples/custom-pom-installation.html

mvn install:install-file -Dfile=lwjgl-natives-2.9.0.jar -DpomFile=pom.xml

[INFO] Installing /Users/rallen/Documents/Devel/Overtone/overtone/lwjgl-natives/lwjgl-natives-2.9.0.jar to /Users/rallen/.m2/repository/shadertone/lwjgl-natives/2.9.0/lwjgl-natives-2.9.0.jar
[INFO] Installing /Users/rallen/Documents/Devel/Overtone/overtone/lwjgl-natives/pom.xml to /Users/rallen/.m2/repository/shadertone/lwjgl-natives/2.9.0/lwjgl-natives-2.9.0.pom

3) Local tryout

over in shadertone or sot

comment out [org.lwjgl.lwjgl/lwjgl-platform "2.9.0" ...] and associated gunk
add [shadertone/lwjgl-natives "2.9.0"]

lein clean
lein -o deps
lein -o test (or run)

4) Upload to clojars

scp lwjgl-natives-2.9.0.jar pom.xml clojars@clojars.org:
