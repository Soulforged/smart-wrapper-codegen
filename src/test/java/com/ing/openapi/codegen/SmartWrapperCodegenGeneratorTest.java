package com.ing.openapi.codegen;

import org.junit.Test;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.languages.AbstractJavaCodegen;

import java.util.Collections;

/***
 * This test allows you to easily launch your code generation software under a debugger.
 * Then run this test under debug mode.  You will be able to step through your java code 
 * and then see the results in the out directory. 
 *
 * To experiment with debugging your code generator:
 * 1) Set a break point in MyCodegenGenerator.java in the postProcessOperationsWithModels() method.
 * 2) To launch this test in Eclipse: right-click | Debug As | JUnit Test
 *
 */
public class SmartWrapperCodegenGeneratorTest {

  // use this test to launch you code generator in the debugger.
  // this allows you to easily set break points in MyclientcodegenGenerator.
  @Test
  public void launchCodeGenerator() {
    // to understand how the 'openapi-generator-cli' module is using 'CodegenConfigurator', have a look at the 'Generate' class:
    // https://github.com/OpenAPITools/openapi-generator/blob/master/modules/openapi-generator-cli/src/main/java/org/openapitools/codegen/cmd/Generate.java 
    final CodegenConfigurator configurator = new CodegenConfigurator()
            .addGlobalProperty("wrappedGeneratorName", "java")
            .addGlobalProperty(CodegenConstants.MODELS, "")
            .addGlobalProperty(CodegenConstants.APIS, "Search")
            .setPackageName("com.generated")
            .setModelPackage("com.generated.model")
            .setApiPackage("com.generated.api")
            .setTemplateDir("src/main/resources/Java")
            .addAdditionalProperty("operationsToGenerate", "v5InvolvedPartiesSearchPost")
            .addAdditionalProperty("useBeanValidation", "true")
            .addAdditionalProperty(AbstractJavaCodegen.DATE_LIBRARY, "java8")
            .addAdditionalProperty(CodegenConstants.LIBRARY, "webclient")
            .setGeneratorName("smart-wrapper") // use this codegen library
            .setInputSpec(getClass().getClassLoader().getResource("petstore.yaml").getPath()) // sample OpenAPI file
            .setOutputDir("out/smart-wrapper-codegen"); // output directory


    final ClientOptInput clientOptInput = configurator.toClientOptInput();
    DefaultGenerator generator = new DefaultGenerator();
    generator.opts(clientOptInput).generate();
  }
}