# SaneScala

`SaneScala` is a scala compiler plugin.


## Compilation

```bash
sbt package
```

## Usage

```bash
export PLUGIN=target/scala-2.12/sane-scala_2.12-0.1.0.jar
scalac -classpath $PLUGIN -Xplugin:$PLUGIN file/to/be/compiled.scala
```
