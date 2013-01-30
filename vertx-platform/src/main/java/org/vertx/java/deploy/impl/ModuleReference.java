/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package org.vertx.java.deploy.impl;

/**
 *
 * This class could benefit from some refactoring
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
class ModuleReference {
  final VerticleManager mgr;
  final String moduleKey;
  final ModuleClassLoader mcl;
  int refCount = 0;
  private VerticleFactory factory;

  ModuleReference(final VerticleManager mgr, final String moduleKey, final ModuleClassLoader mcl) {
    this.mgr = mgr;
    this.moduleKey = moduleKey;
    this.mcl = mcl;
  }

  synchronized void incRef() {
    refCount++;
  }

  synchronized void decRef() {
    refCount--;
    if (refCount == 0) {
      mgr.modules.remove(moduleKey);
      mcl.close();
      if (factory != null) {
        factory.close();
      }
    }
  }

  // We load the VerticleFactory class using the module classloader - this allows
  // us to put language implementations in modules
  // And we maintain a single VerticleFactory per classloader
  public synchronized VerticleFactory getVerticleFactory(String factoryName, VerticleManager mgr)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    if (factory == null) {
      Class clazz = mcl.loadClass(factoryName);
      factory = (VerticleFactory)clazz.newInstance();
      // Sanity check - verticle factories must always be loaded by the mcl otherwise
      // you can get strange effects - e.g. in Rhino if a script subsequently tries to load a Java class
      // it sometimes uses the script classloader (not the context classloader) and if this is the system classloader
      // then it won't find the class if it's in a module
      // This can happen if the user puts verticle factory classes on the system classpath - so we check this
      // here and abort if so
      if (!(factory.getClass().getClassLoader() instanceof ModuleClassLoader)) {
        throw new IllegalStateException("Don't add VerticleFactory classes to the system classpath");
      }
      factory.init(mgr, mcl);
    }
    return factory;
  }

}
