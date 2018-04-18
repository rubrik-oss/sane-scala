# SaneScala
[![Build Status](https://img.shields.io/travis/rubrik-oss/sane-scala.svg)](https://travis-ci.org/rubrik-oss/sane-scala)
[![Test Coverage](https://img.shields.io/codecov/c/github/rubrik-oss/sane-scala.svg)](https://codecov.io/gh/rubrik-oss/sane-scala)
[![Codacy grade](https://img.shields.io/codacy/grade/1557966c5c134561bb6d476d294478f5.svg)](https://app.codacy.com/app/sujeet_2/sane-scala)

`SaneScala` is a scala compiler plugin that flags troublesome
anti-patterns in scala code.


## Development

### Prerequisites

Install linters:
```bash
# Install sclinter
mkdir -p /usr/local/lib/sclinter
wget https://github.com/scaledata/sclinter/releases/download/0.1.0/sclinter.zip
unzip -d /usr/local/lib/sclinter sclinter.zip
rm sclinter.zip

# Install scalastyle
mkdir -p /usr/local/lib/scalastyle
wget -O /usr/local/lib/scalastyle/scalastyle.jar https://goo.gl/XtZqcX
```

### Compilation

```bash
sbt package
```

### Usage

```bash
export PLUGIN=target/scala-2.12/sane-scala_2.12-0.1.0.jar
scalac -classpath $PLUGIN -Xplugin:$PLUGIN file/to/be/compiled.scala
```
