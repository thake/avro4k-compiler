# Avro compiler for avro4k

This is a generator for avro4k compatible kotlin source files. The generator is based on 
the avro java compiler.

Unsupported features:
- Protocol generation
- Union types other than a Union with null

Additional features:
- Kotlin data classes may have a different name than avro records.
