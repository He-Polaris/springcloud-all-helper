package com.chippy.feign.support.definition;

import cn.hutool.core.lang.ClassScanner;
import cn.hutool.json.JSONUtil;
import com.chippy.feign.support.api.processor.FeignClientProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PathMatcher;
import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FeignClientHelper元素信息解析器
 *
 * @author chippy
 */
@Slf4j
public class FeignClientDefinitionResolver implements ApplicationContextAware, InitializingBean {

    private static final String SPRING_APPLICATION_NAME = "spring.application.name";
    private StringBuilder scannerPackages = new StringBuilder();
    private PathMatcher pathMatcher;
    private ApplicationContext applicationContext;
    private String server;

    public FeignClientDefinitionResolver() {
        this.pathMatcher = new AntPathMatcher();
    }

    public FeignClientDefinitionResolver(PathMatcher pathMatcher) {
        this.pathMatcher = pathMatcher;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.server = applicationContext.getEnvironment().getProperty(SPRING_APPLICATION_NAME);
    }

    public void init() {
        Map<String, FeignClientDefinition.Element> elements = new HashMap<>();
        for (String scannerPackage : this.scannerPackages.toString().split(",")) {
            for (Class<?> clazz : ClassScanner.scanPackage(scannerPackage)) {
                if (this.isFeignClient(clazz)) {
                    elements.putAll(this.resolveClazz(clazz));
                }
            }
        }
        // 处理自定义追加类
        elements.putAll(this.classForName(this.getAppendClassName()));
        this.initFeignClientDefinition(elements);
        if (log.isDebugEnabled()) {
            log.debug("feign client element list -> [{}]", JSONUtil.toJsonStr(FeignClientDefinition.elements()));
        }
    }

    private void initScannerPackages() {
        Map<String, Object> enableFeignClientAnnotations =
            applicationContext.getBeansWithAnnotation(EnableFeignClients.class);
        enableFeignClientAnnotations.forEach((k, clazz) -> {
            EnableFeignClients enableFeignClients =
                AnnotationUtils.findAnnotation(clazz.getClass(), EnableFeignClients.class);
            if (null != enableFeignClients) {
                String[] enableFeignClientsPackages =
                    this.getScannerPackages(clazz.getClass().getPackage().getName(), enableFeignClients.value());
                for (String enableFeignClientsPackage : enableFeignClientsPackages) {
                    scannerPackages.append(enableFeignClientsPackage).append(",");
                }
            }
        });
    }

    private String[] getScannerPackages(String defaultScannerPackage, String[] feignClientsDefinitionPackages) {
        // 如果注解设置为空, 则将设置注解处做为切入点进行扫描
        return feignClientsDefinitionPackages.length < 1 ? new String[] {defaultScannerPackage} :
            feignClientsDefinitionPackages;
    }

    /**
     * 追加非基础包路径得FeignClient类信息
     *
     * @return java.util.List<java.lang.String>
     * @author chippy
     */
    public List<String> append() {
        return null;
    }

    private List<String> getAppendClassName() {
        List<String> appendFeignClients = this.append();
        if (null == appendFeignClients || appendFeignClients.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("未指定附加类得加载路径");
            }
            return Collections.emptyList();
        }
        return appendFeignClients;
    }

    private Map<String, FeignClientDefinition.Element> classForName(List<String> scannerClassFullPaths) {
        Map<String, FeignClientDefinition.Element> elements = new HashMap<>(scannerClassFullPaths.size());
        for (String classFullPath : scannerClassFullPaths) {
            Class<?> clazz;
            try {
                clazz = Class.forName(classFullPath);
            } catch (ClassNotFoundException e) {
                if (log.isErrorEnabled()) {
                    log.error("路径[" + classFullPath + "]未反射出类");
                }
                continue;
            }

            if (!this.isFeignClient(clazz)) {
                if (log.isTraceEnabled()) {
                    log.trace("当前类不是FeignClient -> " + classFullPath);
                }
                continue; // is not feign client -> continue;
            }
            elements.putAll(this.resolveClazz(clazz));
        }
        return elements;
    }

    private Map<String, FeignClientDefinition.Element> resolveClazz(Class<?> clazz) {
        Map<String, FeignClientDefinition.Element> feignClientDefinitionInfo = new HashMap<>();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            PostMapping postMapping = method.getAnnotation(PostMapping.class);
            GetMapping getMapping = method.getAnnotation(GetMapping.class);
            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
            PutMapping putMapping = method.getAnnotation(PutMapping.class);
            PatchMapping patchMapping = method.getAnnotation(PatchMapping.class);
            DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
            if (null != postMapping || null != getMapping || null != requestMapping || null != putMapping
                || null != patchMapping || null != deleteMapping) {
                String[] requestPaths =
                    this.resolvePath(postMapping, getMapping, requestMapping, putMapping, patchMapping, deleteMapping);
                FeignClientDefinition.Element element =
                    this.doInitializedFeignDefinition(clazz, feignClientDefinitionInfo, method);
                this.registerFeignClientProcessor(requestPaths, element);
            }
        }
        return feignClientDefinitionInfo;
    }

    private String[] resolvePath(PostMapping postMapping, GetMapping getMapping, RequestMapping requestMapping,
        PutMapping putMapping, PatchMapping patchMapping, DeleteMapping deleteMapping) {
        if (null != postMapping) {
            return postMapping.value();
        }
        if (null != getMapping) {
            return getMapping.value();
        }
        if (null != requestMapping) {
            return requestMapping.value();
        }
        if (null != putMapping) {
            return putMapping.value();
        }
        if (null != patchMapping) {
            return patchMapping.value();
        }
        return deleteMapping.value();
    }

    private FeignClientDefinition.Element doInitializedFeignDefinition(Class<?> clazz,
        Map<String, FeignClientDefinition.Element> feignClientDefinitionInfo, Method method) {
        FeignClientDefinition.Element element =
            new FeignClientDefinition.Element(method.getName(), clazz.getName(), clazz);
        feignClientDefinitionInfo.put(method.getName(), element);
        return element;
    }

    private void registerFeignClientProcessor(String[] requestPaths, FeignClientDefinition.Element element) {
        final Map<String, FeignClientProcessor> feignClientProcessors =
            applicationContext.getBeansOfType(FeignClientProcessor.class);
        feignClientProcessors.forEach((k, feignClientProcessor) -> this
            .doRegisterFeignClientProcessor(requestPaths, element, feignClientProcessor));
    }

    private void doRegisterFeignClientProcessor(String[] requestPaths, FeignClientDefinition.Element element,
        FeignClientProcessor feignClientProcessor) {
        final List<String> includePathPatterns = feignClientProcessor.getIncludePathPattern();
        final List<String> excludePathPatterns = feignClientProcessor.getExcludePathPattern();
        if (CollectionUtils.isEmpty(includePathPatterns)) {
            return;
        }

        boolean isMatch = false;
        for (String includePattern : includePathPatterns) {
            for (String requestPath : requestPaths) {
                if (pathMatcher.match(includePattern, requestPath)) {
                    isMatch = true;
                    break;
                }
            }
        }
        if (!CollectionUtils.isEmpty(excludePathPatterns)) {
            for (String excludePattern : excludePathPatterns) {
                for (String requestPath : requestPaths) {
                    if (pathMatcher.match(excludePattern, requestPath)) {
                        isMatch = false;
                        break;
                    }
                }
            }
        }

        if (isMatch) {
            FeignClientProcessorRegistry.register(element.getFullPath() + element.getMethod(), feignClientProcessor);
        }
    }

    private void initFeignClientDefinition(Map<String, FeignClientDefinition.Element> elements) {
        FeignClientDefinition instance = FeignClientDefinition.getInstance();
        instance.setCache(elements);
        instance.setServer(server);
    }

    private boolean isFeignClient(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getName().contains("FeignClient")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.initScannerPackages();
        this.init();
    }
}
