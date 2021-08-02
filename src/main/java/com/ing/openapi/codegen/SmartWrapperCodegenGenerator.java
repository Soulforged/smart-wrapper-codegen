package com.ing.openapi.codegen;

import com.samskivert.mustache.Mustache;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.tags.Tag;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenSecurity;
import org.openapitools.codegen.CodegenServer;
import org.openapitools.codegen.CodegenServerVariable;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.api.TemplatingEngineAdapter;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.config.GlobalSettings;
import org.openapitools.codegen.meta.FeatureSet;
import org.openapitools.codegen.meta.GeneratorMetadata;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 *
 */
public class SmartWrapperCodegenGenerator implements CodegenConfig {

    private CodegenConfig wrappedConfig = null;
    private OpenAPI openAPI;
    private String inputSpec;
    private Map<String, Object> additionalProperties = new HashMap<>();
    private final List<CliOption> cliOptions = new ArrayList<>();

    public SmartWrapperCodegenGenerator() {
        cliOptions.add(CliOption.newBoolean("fitModelToAPI",
                "if NO modelsToGenerate is specified: should we fit the model classes only to the ones used by" +
                        "the subset of APIs we want to generate?", true));
        cliOptions.add(CliOption.newString("operationsToGenerate",
                "which operations of an API should be generated, empty means all. Defaults to empty"));
        cliOptions.add(CliOption.newString("apisToGenerate",
                "similar to the global property, but used here to avoid the limitations associated with it," +
                        "namely: the global property is only set if generateApis is true and we don't always want that. " +
                        "Defaults to empty"));
    }

    public CodegenConfig getWrappedConfig() {
        if (wrappedConfig == null){
            String wrappedConfigName = GlobalSettings.getProperty("wrappedGeneratorName");
            wrappedConfigName = wrappedConfigName == null ? "java" : wrappedConfigName;
            final CodegenConfigurator configurator = new CodegenConfigurator()
                    .setGeneratorName(wrappedConfigName)
                    .setInputSpec(getInputSpec());
            wrappedConfig = configurator.toClientOptInput().getConfig();
            wrappedConfig.cliOptions().addAll(cliOptions);
        }
        return wrappedConfig;
    }

    @Override
    public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
        Map<String, Object> delegateResult = getWrappedConfig().postProcessOperationsWithModels(objs, allModels);
        Set<String> operationsToGenerate = getOperationsToGenerate();
        if (!operationsToGenerate.isEmpty() && fitModelToAPI()) {
            ((Map<String, Object>)delegateResult.get("operations")).entrySet().stream().forEach(entry -> {
                if (entry.getKey().equals("operation")){
                    List<CodegenOperation> opList = (List<CodegenOperation>) entry.getValue();
                    List<CodegenOperation> filtered = opList.stream()
                            .filter(op -> operationsToGenerate.contains(op.operationId))
                            .collect(Collectors.toList());
                    entry.setValue(filtered);
                }
            });
            Set<String> modelNames = !allModels.isEmpty() ? allModels.stream()
                    .map(m -> m instanceof Map ? ((Map)m).get("importPath").toString() : "")
                    .collect(Collectors.toSet()) : usedModelsFromAPIs();
            System.out.println("MODELS: " + modelNames);
            System.out.println("ORG IMP: " + delegateResult.get("imports"));
            List<Map<String,String>> filtered = ((List<Map<String,String>>)delegateResult.get("imports")).stream()
//                    .map(s -> s.get("import"))
                    .filter(imp -> modelNames.contains(imp.get("classname")))
                    .collect(Collectors.toList());
            System.out.println("FILTERED: " + filtered);
            delegateResult.put("imports", filtered);
        }

        return delegateResult;
    }

    @Override
    public Map<String, Object> postProcessAllModels(Map<String, Object> objs) {
        Map<String, Object> filtered = objs;
        if (fitModelToAPI()) {
            Set<String> usedModels = usedModelsFromAPIs();
            System.out.println(usedModels);
            filtered = new HashMap<>();
            filterOutNonUsedModels(objs, usedModels, filtered);
        }
        return getWrappedConfig().postProcessAllModels(filtered);
    }

    private void filterOutNonUsedModels(Map<String, Object> allObjs, Set<String> usedModels, Map<String, Object> acc) {
        usedModels.forEach(modelName -> {
            Object o = allObjs.get(modelName);
            if (o != null) {
                acc.put(modelName, o);
                if (o instanceof Map) {
                    addSubTree((Map<String, Object>) o, allObjs, acc);
                }
            }
        });
    }

    private void addSubTree(Map<String, Object> currentObj, Map<String, Object> allObjs, Map<String, Object> acc) {
        List<Map<String, Object>> importedModels = (List) currentObj.get("imports");
        String modelPackage = (String) currentObj.get("package");
        importedModels.stream().filter(modelMap -> modelMap.get("import").toString().startsWith(modelPackage)).forEach(modelMap -> {
            String model = ((String) modelMap.get("import"));
            String[] splittedName = model.split("\\.");
            String baseName = splittedName[splittedName.length - 1];
            Map<String, Object> nextObject = (Map<String, Object>) allObjs.get(baseName);
            if (nextObject == null){
                return;
            }
            if (!acc.containsKey(baseName)) {
                acc.put(baseName, allObjs.get(baseName));
                addSubTree(nextObject, allObjs, acc);
            }
        });
    }

    private Set<String> usedModelsFromAPIs() {
        Map<String, List<CodegenOperation>> paths = processPaths(this.openAPI.getPaths());
        final Set<String> apisToGenerate = getApisToGenerate();
        Set<CodegenOperation> ops = paths.keySet().stream().filter(path -> apisToGenerate.isEmpty() || apisToGenerate.contains(path))
                .map(paths::get).flatMap(Collection::stream).collect(Collectors.toSet());
        Set<String> filteredOps = ops.stream().flatMap(cop -> cop.responses.stream()
                .filter(resp -> resp.dataType != null && !resp.dataType.contains("<"))
                .map(resp -> resp.dataType)).collect(Collectors.toSet());
        filteredOps.addAll(ops.stream().flatMap(cop -> cop.bodyParams.stream()
                .filter(req -> req.dataType != null && !req.dataType.contains("<"))
                .map(req -> req.dataType)).collect(Collectors.toSet()));
        return filteredOps;
    }

    private HashSet<String> getApisToGenerate() {
        return Optional.ofNullable(GlobalSettings.getProperty(CodegenConstants.APIS))
                .filter(s -> !s.isEmpty())
                .map(apiNames -> new HashSet<>(Arrays.asList(apiNames.split(","))))
                .orElse(Optional.ofNullable(additionalProperties().get("apisToGenerate"))
                        .map(apisToGenerate -> new HashSet<>(Arrays.asList(apisToGenerate.toString().split(","))))
                        .orElse(new HashSet<>()));
    }

    public Map<String, List<CodegenOperation>> processPaths(Paths paths) {
        Map<String, List<CodegenOperation>> ops = new TreeMap<>();
        // when input file is not valid and doesn't contain any paths
        if (paths == null) {
            return ops;
        }
        final Set<String> operationsToGenerate = getOperationsToGenerate();
        for (String resourcePath : paths.keySet()) {
            PathItem path = paths.get(resourcePath);
            processOperation(resourcePath, "get", path.getGet(), ops, path, operationsToGenerate);
            processOperation(resourcePath, "head", path.getHead(), ops, path, operationsToGenerate);
            processOperation(resourcePath, "put", path.getPut(), ops, path, operationsToGenerate);
            processOperation(resourcePath, "post", path.getPost(), ops, path, operationsToGenerate);
            processOperation(resourcePath, "delete", path.getDelete(), ops, path, operationsToGenerate);
            processOperation(resourcePath, "patch", path.getPatch(), ops, path, operationsToGenerate);
            processOperation(resourcePath, "options", path.getOptions(), ops, path, operationsToGenerate);
            processOperation(resourcePath, "trace", path.getTrace(), ops, path, operationsToGenerate);
        }
        return ops;
    }

    private Set<String> getOperationsToGenerate() {
        final Set<String> operationsToGenerate = Optional.ofNullable(additionalProperties().get("operationsToGenerate"))
                .filter(prop -> !prop.toString().isEmpty())
                .map(prop -> new HashSet<>(Arrays.asList(prop.toString().split(","))))
                .orElse(new HashSet<>());
        System.out.println("OPS: " + operationsToGenerate);
        return operationsToGenerate;
    }

    private boolean fitModelToAPI(){
        return Optional.ofNullable(additionalProperties().get("fitModelToAPI")).map(a -> (Boolean)a)
                .orElse(true);
    }

    private void processOperation(String resourcePath, String httpMethod, Operation operation,
                                  Map<String, List<CodegenOperation>> operations, PathItem path,
                                  Set<String> operationsToGenerate) {
        if (operation == null){
            return;
        }

        List<Tag> tags = new ArrayList<>();
        List<String> tagNames = operation.getTags();
        List<Tag> swaggerTags = openAPI.getTags();
        if (tagNames != null) {
            if (swaggerTags == null) {
                for (String tagName : tagNames) {
                    tags.add(new Tag().name(tagName));
                }
            } else {
                for (String tagName : tagNames) {
                    boolean foundTag = false;
                    for (Tag tag : swaggerTags) {
                        if (tag.getName().equals(tagName)) {
                            tags.add(tag);
                            foundTag = true;
                            break;
                        }
                    }

                    if (!foundTag) {
                        tags.add(new Tag().name(tagName));
                    }
                }
            }
        }

        if (tags.isEmpty()) {
            tags.add(new Tag().name("default"));
        }

        /*
         build up a set of parameter "ids" defined at the operation level
         per the swagger 2.0 spec "A unique parameter is defined by a combination of a name and location"
          i'm assuming "location" == "in"
        */
        Set<String> operationParameters = new HashSet<>();
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                operationParameters.add(generateParameterId(parameter));
            }
        }

        //need to propagate path level down to the operation
        if (path.getParameters() != null) {
            for (Parameter parameter : path.getParameters()) {
                //skip propagation if a parameter with the same name is already defined at the operation level
                if (!operationParameters.contains(generateParameterId(parameter))) {
                    operation.addParametersItem(parameter);
                }
            }
        }

        for (Tag tag : tags) {
            CodegenOperation codegenOperation = this.fromOperation(resourcePath, httpMethod, operation, path.getServers());
            codegenOperation.tags = new ArrayList<>(tags);
            if (operationsToGenerate.isEmpty() || operationsToGenerate.contains(codegenOperation.operationId)) {
                this.addOperationToGroup(this.sanitizeTag(tag.getName()), resourcePath, operation, codegenOperation, operations);
            }
        }
    }

    private static String generateParameterId(Parameter parameter) {
        return parameter.getName() + ":" + parameter.getIn();
    }

    @Override
    public String outputFolder() {
        return getWrappedConfig().outputFolder();
    }

    @Override
    public String getInputSpec() {
        return inputSpec;
    }

    @Override
    public GeneratorMetadata getGeneratorMetadata() {
        return getWrappedConfig().getGeneratorMetadata();
    }

    @Override
    public CodegenType getTag() {
        return getWrappedConfig().getTag();
    }

    @Override
    public String getName() {
        return "smart-wrapper";
    }

    @Override
    public String getHelp() {
        return getWrappedConfig().getHelp();
    }

    @Override
    public Map<String, Object> additionalProperties() {
        return getWrappedConfig().additionalProperties();
    }

    @Override
    public Map<String, String> serverVariableOverrides() {
        return getWrappedConfig().serverVariableOverrides();
    }

    @Override
    public Map<String, Object> vendorExtensions() {
        return getWrappedConfig().vendorExtensions();
    }

    @Override
    public String testPackage() {
        return getWrappedConfig().testPackage();
    }

    @Override
    public String apiPackage() {
        return getWrappedConfig().apiPackage();
    }

    @Override
    public String apiFileFolder() {
        return getWrappedConfig().apiFileFolder();
    }

    @Override
    public String apiTestFileFolder() {
        return getWrappedConfig().apiTestFileFolder();
    }

    @Override
    public String apiDocFileFolder() {
        return getWrappedConfig().apiDocFileFolder();
    }

    @Override
    public String fileSuffix() {
        return getWrappedConfig().fileSuffix();
    }

    @Override
    public String templateDir() {
        return getWrappedConfig().templateDir();
    }

    @Override
    public String modelFileFolder() {
        return getWrappedConfig().modelFileFolder();
    }

    @Override
    public String modelTestFileFolder() {
        return getWrappedConfig().modelTestFileFolder();
    }

    @Override
    public String modelDocFileFolder() {
        return getWrappedConfig().modelDocFileFolder();
    }

    @Override
    public String modelPackage() {
        return getWrappedConfig().modelPackage();
    }

    @Override
    public String embeddedTemplateDir() {
        return getWrappedConfig().embeddedTemplateDir();
    }

    @Override
    public String toApiName(String name) {
        return getWrappedConfig().toApiName(name);
    }

    @Override
    public String toApiVarName(String name) {
        return getWrappedConfig().toApiVarName(name);
    }

    @Override
    public String toModelName(String name) {
        return getWrappedConfig().toModelName(name);
    }

    @Override
    public String toParamName(String name) {
        return getWrappedConfig().toParamName(name);
    }

    @Override
    public String escapeText(String text) {
        return getWrappedConfig().escapeText(text);
    }

    @Override
    public String escapeTextWhileAllowingNewLines(String text) {
        return getWrappedConfig().escapeTextWhileAllowingNewLines(text);
    }

    @Override
    public String encodePath(String text) {
        return getWrappedConfig().encodePath(text);
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return getWrappedConfig().escapeUnsafeCharacters(input);
    }

    @Override
    public String escapeReservedWord(String name) {
        return getWrappedConfig().escapeReservedWord(name);
    }

    @Override
    public String escapeQuotationMark(String input) {
        return getWrappedConfig().escapeQuotationMark(input);
    }

    @Override
    public String getTypeDeclaration(Schema schema) {
        return getWrappedConfig().getTypeDeclaration(schema);
    }

    @Override
    public String getTypeDeclaration(String name) {
        return getWrappedConfig().getTypeDeclaration(name);
    }

    @Override
    public void processOpts() {
        getWrappedConfig().additionalProperties().putAll(additionalProperties);
        System.out.println(getWrappedConfig().additionalProperties());
        getWrappedConfig().processOpts();
    }

    @Override
    public List<CliOption> cliOptions() {
        return getWrappedConfig().cliOptions();
    }

    @Override
    public String generateExamplePath(String path, Operation operation) {
        return getWrappedConfig().generateExamplePath(path, operation);
    }

    @Override
    public Set<String> reservedWords() {
        return getWrappedConfig().reservedWords();
    }

    @Override
    public List<SupportingFile> supportingFiles() {
        return getWrappedConfig().supportingFiles();
    }

    @Override
    public void setInputSpec(String inputSpec) {
        this.inputSpec = inputSpec;
        getWrappedConfig().setInputSpec(inputSpec);
    }

    @Override
    public String getOutputDir() {
        return getWrappedConfig().getOutputDir();
    }

    @Override
    public void setOutputDir(String dir) {
        getWrappedConfig().setOutputDir(dir);
    }

    @Override
    public CodegenModel fromModel(String name, Schema schema) {
        return getWrappedConfig().fromModel(name, schema);
    }

    @Override
    public CodegenOperation fromOperation(String resourcePath, String httpMethod, Operation operation, List<Server> servers) {
        return getWrappedConfig().fromOperation(resourcePath, httpMethod, operation, servers);
    }

    @Override
    public List<CodegenSecurity> fromSecurity(Map<String, SecurityScheme> schemas) {
        return getWrappedConfig().fromSecurity(schemas);
    }

    @Override
    public List<CodegenServer> fromServers(List<Server> servers) {
        return getWrappedConfig().fromServers(servers);
    }

    @Override
    public List<CodegenServerVariable> fromServerVariables(Map<String, ServerVariable> variables) {
        return getWrappedConfig().fromServerVariables(variables);
    }

    @Override
    public Set<String> defaultIncludes() {
        return getWrappedConfig().defaultIncludes();
    }

    @Override
    public Map<String, String> typeMapping() {
        return getWrappedConfig().typeMapping();
    }

    @Override
    public Map<String, String> instantiationTypes() {
        return getWrappedConfig().instantiationTypes();
    }

    @Override
    public Map<String, String> importMapping() {
        return getWrappedConfig().importMapping();
    }

    @Override
    public Map<String, String> apiTemplateFiles() {
        return getWrappedConfig().apiTemplateFiles();
    }

    @Override
    public Map<String, String> modelTemplateFiles() {
        return getWrappedConfig().modelTemplateFiles();
    }

    @Override
    public Map<String, String> apiTestTemplateFiles() {
        return getWrappedConfig().apiTestTemplateFiles();
    }

    @Override
    public Map<String, String> modelTestTemplateFiles() {
        return getWrappedConfig().modelTestTemplateFiles();
    }

    @Override
    public Map<String, String> apiDocTemplateFiles() {
        return getWrappedConfig().apiDocTemplateFiles();
    }

    @Override
    public Map<String, String> modelDocTemplateFiles() {
        return getWrappedConfig().modelDocTemplateFiles();
    }

    @Override
    public Set<String> languageSpecificPrimitives() {
        return getWrappedConfig().languageSpecificPrimitives();
    }

    @Override
    public Map<String, String> reservedWordsMappings() {
        return getWrappedConfig().reservedWordsMappings();
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        getWrappedConfig().preprocessOpenAPI(openAPI);
    }

    @Override
    public void processOpenAPI(OpenAPI openAPI) {
        getWrappedConfig().processOpenAPI(openAPI);
    }

    @Override
    public Mustache.Compiler processCompiler(Mustache.Compiler compiler) {
        return getWrappedConfig().processCompiler(compiler);
    }

    @Override
    public TemplatingEngineAdapter processTemplatingEngine(TemplatingEngineAdapter templatingEngine) {
        return getWrappedConfig().processTemplatingEngine(templatingEngine);
    }

    @Override
    public String sanitizeTag(String tag) {
        return getWrappedConfig().sanitizeTag(tag);
    }

    @Override
    public String toApiFilename(String name) {
        return getWrappedConfig().toApiFilename(name);
    }

    @Override
    public String toModelFilename(String name) {
        return getWrappedConfig().toModelFilename(name);
    }

    @Override
    public String toApiTestFilename(String name) {
        return getWrappedConfig().toApiTestFilename(name);
    }

    @Override
    public String toModelTestFilename(String name) {
        return getWrappedConfig().toModelTestFilename(name);
    }

    @Override
    public String toApiDocFilename(String name) {
        return getWrappedConfig().toApiDocFilename(name);
    }

    @Override
    public String toModelDocFilename(String name) {
        return getWrappedConfig().toModelDocFilename(name);
    }

    @Override
    public String toModelImport(String name) {
        return getWrappedConfig().toModelImport(name);
    }

    @Override
    public Map<String, String> toModelImportMap(String name) {
        return getWrappedConfig().toModelImportMap(name);
    }

    @Override
    public String toApiImport(String name) {
        return getWrappedConfig().toApiImport(name);
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        getWrappedConfig().addOperationToGroup(tag, resourcePath, operation, co, operations);
    }

    @Override
    public Map<String, Object> updateAllModels(Map<String, Object> objs) {
        return getWrappedConfig().updateAllModels(objs);
    }

    @Override
    public void postProcess() {
        getWrappedConfig().postProcess();
    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        return getWrappedConfig().postProcessModels(objs);
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        return getWrappedConfig().postProcessSupportingFileData(objs);
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        getWrappedConfig().postProcessModelProperty(model, property);
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        getWrappedConfig().postProcessParameter(parameter);
    }

    @Override
    public String modelFilename(String templateName, String modelName) {
        return getWrappedConfig().modelFilename(templateName, modelName);
    }

    @Override
    public String apiFilename(String templateName, String tag) {
        return getWrappedConfig().apiFilename(templateName, tag);
    }

    @Override
    public String apiTestFilename(String templateName, String tag) {
        return getWrappedConfig().apiTestFilename(templateName, tag);
    }

    @Override
    public String apiDocFilename(String templateName, String tag) {
        return getWrappedConfig().apiDocFilename(templateName, tag);
    }

    @Override
    public boolean shouldOverwrite(String filename) {
        return getWrappedConfig().shouldOverwrite(filename);
    }

    @Override
    public boolean isSkipOverwrite() {
        return getWrappedConfig().isSkipOverwrite();
    }

    @Override
    public void setSkipOverwrite(boolean skipOverwrite) {
        getWrappedConfig().setSkipOverwrite(skipOverwrite);
    }

    @Override
    public boolean isRemoveOperationIdPrefix() {
        return getWrappedConfig().isRemoveOperationIdPrefix();
    }

    @Override
    public void setRemoveOperationIdPrefix(boolean removeOperationIdPrefix) {
        getWrappedConfig().setRemoveOperationIdPrefix(removeOperationIdPrefix);
    }

    @Override
    public boolean isSkipOperationExample() {
        return getWrappedConfig().isSkipOperationExample();
    }

    @Override
    public void setSkipOperationExample(boolean skipOperationExample) {
        getWrappedConfig().setSkipOperationExample(skipOperationExample);
    }

    @Override
    public boolean isHideGenerationTimestamp() {
        return getWrappedConfig().isHideGenerationTimestamp();
    }

    @Override
    public void setHideGenerationTimestamp(boolean hideGenerationTimestamp) {
        getWrappedConfig().setHideGenerationTimestamp(hideGenerationTimestamp);
    }

    @Override
    public Map<String, String> supportedLibraries() {
        return getWrappedConfig().supportedLibraries();
    }

    @Override
    public void setLibrary(String library) {
        getWrappedConfig().setLibrary(library);
    }

    @Override
    public String getLibrary() {
        return getWrappedConfig().getLibrary();
    }

    @Override
    public void setGitHost(String gitHost) {
        getWrappedConfig().setGitHost(gitHost);
    }

    @Override
    public String getGitHost() {
        return getWrappedConfig().getGitHost();
    }

    @Override
    public void setGitUserId(String gitUserId) {
        getWrappedConfig().setGitUserId(gitUserId);
    }

    @Override
    public String getGitUserId() {
        return getWrappedConfig().getGitUserId();
    }

    @Override
    public void setGitRepoId(String gitRepoId) {
        getWrappedConfig().setGitRepoId(gitRepoId);
    }

    @Override
    public String getGitRepoId() {
        return getWrappedConfig().getGitRepoId();
    }

    @Override
    public void setReleaseNote(String releaseNote) {
        getWrappedConfig().setReleaseNote(releaseNote);
    }

    @Override
    public String getReleaseNote() {
        return getWrappedConfig().getReleaseNote();
    }

    @Override
    public void setHttpUserAgent(String httpUserAgent) {
        getWrappedConfig().setHttpUserAgent(httpUserAgent);
    }

    @Override
    public String getHttpUserAgent() {
        return getWrappedConfig().getHttpUserAgent();
    }

    @Override
    public void setDocExtension(String docExtension) {
        getWrappedConfig().setDocExtension(docExtension);
    }

    @Override
    public String getDocExtension() {
        return getWrappedConfig().getDocExtension();
    }

    @Override
    public void setIgnoreFilePathOverride(String ignoreFileOverride) {
        getWrappedConfig().setIgnoreFilePathOverride(ignoreFileOverride);
    }

    @Override
    public String getIgnoreFilePathOverride() {
        return getWrappedConfig().getIgnoreFilePathOverride();
    }

    @Override
    public String toBooleanGetter(String name) {
        return getWrappedConfig().toBooleanGetter(name);
    }

    @Override
    public String toSetter(String name) {
        return getWrappedConfig().toSetter(name);
    }

    @Override
    public String toGetter(String name) {
        return getWrappedConfig().toGetter(name);
    }

    @Override
    public String sanitizeName(String name) {
        return getWrappedConfig().sanitizeName(name);
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        getWrappedConfig().postProcessFile(file, fileType);
    }

    @Override
    public boolean isEnablePostProcessFile() {
        return getWrappedConfig().isEnablePostProcessFile();
    }

    @Override
    public void setEnablePostProcessFile(boolean isEnablePostProcessFile) {
        getWrappedConfig().setEnablePostProcessFile(isEnablePostProcessFile);
    }

    @Override
    public void setOpenAPI(OpenAPI openAPI) {
        this.openAPI = openAPI;
        getWrappedConfig().setOpenAPI(openAPI);
    }

    @Override
    public void setTemplatingEngine(TemplatingEngineAdapter s) {
        getWrappedConfig().setTemplatingEngine(s);
    }

    @Override
    public TemplatingEngineAdapter getTemplatingEngine() {
        return getWrappedConfig().getTemplatingEngine();
    }

    @Override
    public boolean isEnableMinimalUpdate() {
        return getWrappedConfig().isEnableMinimalUpdate();
    }

    @Override
    public void setEnableMinimalUpdate(boolean isEnableMinimalUpdate) {
        getWrappedConfig().setEnableMinimalUpdate(isEnableMinimalUpdate);
    }

    @Override
    public boolean isStrictSpecBehavior() {
        return getWrappedConfig().isStrictSpecBehavior();
    }

    @Override
    public void setStrictSpecBehavior(boolean strictSpecBehavior) {
        getWrappedConfig().setStrictSpecBehavior(strictSpecBehavior);
    }

    @Override
    public FeatureSet getFeatureSet() {
        return getWrappedConfig().getFeatureSet();
    }

    @Override
    public boolean isRemoveEnumValuePrefix() {
        return getWrappedConfig().isRemoveEnumValuePrefix();
    }

    @Override
    public void setRemoveEnumValuePrefix(boolean removeEnumValuePrefix) {
        getWrappedConfig().setRemoveEnumValuePrefix(removeEnumValuePrefix);
    }

    @Override
    public Schema unaliasSchema(Schema schema, Map<String, String> usedImportMappings) {
        return getWrappedConfig().unaliasSchema(schema, usedImportMappings);
    }
}