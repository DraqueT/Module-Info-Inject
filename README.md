# Module-Info-Inject
This injects custom module-info.class into non-modular java libraries, allowing them to be used with jlink

This is written in Java 8 and is itself non-modular, mostly because I think that is kind of funny.

Why this exists:
Java archives and libraries which were created in a non-modular way cannot be linked into runnable images. You get an auto-module error, which is super annoying. This utility alleviates the problem and allows you to inject a module class into the jar file, allowing it to be linked properly.

To Use:
- Navigate to the jar you want to inject module-info.class into. 
- Click inject. If the jar itself has no dependencies, you are good to go. (a backup will be created in the same directory.
- It is likely that there will be dependencies that the jar requires before you can compile a module-info.class to inject into it...
- If this is the case, look at the namespaces and classes that the injector lists for you. You will have to track down the packages that contain them, download them, then add *those* jars as dependencies.
- If dependencies themselves have dependencies, you will not be alerted until you try to use jlink. Please be aware of this and only add one module injected jar to your project at a time (otherwise tracking this down can be a nightmare).
- The module-info.java file will also be injected into the jar for reference.
- There are edge cases I have not figured out yet that lead to the injector not catching some dependencies. Again, these will make themselves known when you go to use jlink.

Enjoy.
