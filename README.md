# Module-Info-Inject
This injects custom module-info.class into non-modular java libraries, allowing them to be used with jlink

This is written in Java 8 and is itself non-modular, mostly because I think that is kind of funny.

Why this exists:
Java archives and libraries which were created in a non-modular way cannot be linked into runnable images. You get an auto-module error, which is super annoying. This utility allieviates the problem and allows you to inject a module class into the jar file, allowing it to be linked properly.

To Use:
- Navagate to the jar you want to inject module-info.class into. 
- In the module name field, type the name you wish it to exist under. This is what will be listed as required within your own module-info.java source file.
- In the exports field, give a comma delimited list of all paths you wish the module to export (otherwise they can't be externally seen)
- There is very little in the way of error checking here but it does create backup files any time you modify a jar
- Class compilation errors mean that you're trying to export a path that doesn't exist, have illegal characters, etc. (might do a better job of collecting/reporting this back at a later time)

Enjoy.
