/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tomee.embedded.internal;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.openejb.config.WebModule;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.observer.Observes;
import org.apache.openejb.util.URLs;
import org.apache.openejb.util.reflection.Reflections;
import org.apache.tomee.catalina.TomcatWebAppBuilder;

import java.io.File;
import java.net.URL;
import java.util.List;

public class StandardContextCustomizer {
    private final WebModule module;

    public StandardContextCustomizer(final WebModule webModule) {
        module = webModule;
    }

    public void customize(@Observes final LifecycleEvent event) {
        final Object data = event.getSource();
        if (!StandardContext.class.isInstance(data)) {
            return;
        }

        final StandardContext context = StandardContext.class.cast(data);
        final String contextRoot = module.getContextRoot();
        final String path = context.getPath();
        final boolean rightPath = (path.isEmpty() && contextRoot.equals(path))
                || (contextRoot.startsWith("/") ? contextRoot : '/' + contextRoot).equals(path);
        if (!rightPath) {
            return;
        }

        switch (event.getType()) {
            case Lifecycle.BEFORE_START_EVENT:
                final WebResourceRoot resources = new StandardRoot(context);
                context.setResources(resources);
                if (!module.getProperties().containsKey("fakeJarLocation")) {
                    context.setDocBase(module.getJarLocation());
                }

                // move last fake folder, tomcat is broken without it so we can't remove it
                final List allResources = List.class.cast(Reflections.get(resources, "allResources"));
                final Object mainResources = allResources.remove(1);
                allResources.add(mainResources);

                for (final URL url : module.getScannableUrls()) {
                    final File file = URLs.toFile(url);
                    final String absolutePath = file.getAbsolutePath();
                    if (file.isDirectory()) {
                        resources.createWebResourceSet(WebResourceRoot.ResourceSetType.CLASSES_JAR, "/WEB-INF/classes", absolutePath, "", "/");
                        if (new File(file, "META-INF/resources").exists()) {
                            resources.createWebResourceSet(WebResourceRoot.ResourceSetType.RESOURCE_JAR, "/", absolutePath, "", "/META-INF/resources");
                        }
                    } else {
                        resources.createWebResourceSet(WebResourceRoot.ResourceSetType.CLASSES_JAR, "/WEB-INF/lib", absolutePath, null, "/");
                        resources.createWebResourceSet(WebResourceRoot.ResourceSetType.RESOURCE_JAR, "/", url, "/META-INF/resources");
                    }
                }
                break;
            case Lifecycle.CONFIGURE_START_EVENT:
                SystemInstance.get().getComponent(TomcatWebAppBuilder.class).setFinderOnContextConfig(StandardContext.class.cast(data), module.appModule());
                break;
            default:
        }
    }
}
