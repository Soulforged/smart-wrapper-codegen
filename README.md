# OpenAPI Generator for the smart-wrapper-codegen library

## Overview
This generator simply wraps your own and adds some custom logic to make the generated code fit your API requirements.

Normally you'd achieve this by providing a list of `apisToGenerate` and/or `modelsToGenerate`, but this becomes highly 
impractical when models explode out in the hundreds and you don't want to generate and/or maintain that many, even less 
in a list of strings in your config. So using this generator wrapper you only need to specify your desired APIs via
`apisToGenerate` and/or `operationsToGenerate` to reduce the original specification to the relevant bits at generation
time.

## What's OpenAPI
The goal of OpenAPI is to define a standard, language-agnostic interface to REST APIs which allows both humans and computers to discover and understand the capabilities of the service without access to source code, documentation, or through network traffic inspection.
When properly described with OpenAPI, a consumer can understand and interact with the remote service with a minimal amount of implementation logic.
Similar to what interfaces have done for lower-level programming, OpenAPI removes the guesswork in calling the service.

Check out [OpenAPI-Spec](https://github.com/OAI/OpenAPI-Specification) for additional information about the OpenAPI project, including additional libraries with support for other languages and more. 

## How do I use this?

TODO