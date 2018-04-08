# SaneScala

`SaneScala` is a scala compiler plugin.


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
