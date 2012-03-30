/*
 * Copyright 2004-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.faces.mvc;

import static org.springframework.faces.webflow.JsfRuntimeInformation.isAtLeastJsf12;
import static org.springframework.faces.webflow.JsfRuntimeInformation.isPortletRequest;

import java.util.Iterator;
import java.util.Map;

import javax.faces.FactoryFinder;
import javax.faces.application.ViewHandler;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.event.PhaseId;
import javax.faces.lifecycle.Lifecycle;
import javax.faces.lifecycle.LifecycleFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.faces.webflow.FacesContextHelper;
import org.springframework.faces.webflow.JsfUtils;
import org.springframework.util.Assert;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.AbstractUrlBasedView;

/**
 * JSF View that renders a transient (stateless) JSF view template. The UIViewRoot will not be saved and thus the JSF
 * lifecycle will not be able to be invoked on postback.
 * 
 * @author Jeremy Grelle
 */
public class JsfView extends AbstractUrlBasedView {

	private Lifecycle facesLifecycle;

	public void afterPropertiesSet() throws Exception {
		super.afterPropertiesSet();
		facesLifecycle = createFacesLifecycle();
	}

	protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		FacesContextHelper facesContextHelper = new FacesContextHelper();
		FacesContext facesContext = facesContextHelper.getFacesContext(getServletContext(), request, response);

		populateRequestMap(facesContext, model);

		JsfUtils.notifyBeforeListeners(PhaseId.RESTORE_VIEW, facesLifecycle, facesContext);

		ViewHandler viewHandler = facesContext.getApplication().getViewHandler();

		if (isAtLeastJsf12() && (!isPortletRequest(facesContext))) {
			viewHandler.initView(facesContext);
		}

		UIViewRoot viewRoot = viewHandler.createView(facesContext, getUrl());
		Assert.notNull(viewRoot, "A JSF view could not be created for " + getUrl());
		viewRoot.setLocale(RequestContextUtils.getLocale(request));
		viewRoot.setTransient(true);

		facesContext.setViewRoot(viewRoot);

		JsfUtils.notifyAfterListeners(PhaseId.RESTORE_VIEW, facesLifecycle, facesContext);

		facesContext.setViewRoot(viewRoot);
		facesContext.renderResponse();
		try {
			logger.debug("Asking faces lifecycle to render");
			facesLifecycle.render(facesContext);
		} finally {
			logger.debug("View rendering complete");
			facesContextHelper.releaseIfNecessary();
		}
	}

	private void populateRequestMap(FacesContext facesContext, Map<String, Object> model) {
		Iterator<String> i = model.keySet().iterator();
		while (i.hasNext()) {
			String key = i.next().toString();
			facesContext.getExternalContext().getRequestMap().put(key, model.get(key));
		}
	}

	private Lifecycle createFacesLifecycle() {
		LifecycleFactory lifecycleFactory = (LifecycleFactory) FactoryFinder
				.getFactory(FactoryFinder.LIFECYCLE_FACTORY);
		return lifecycleFactory.getLifecycle(LifecycleFactory.DEFAULT_LIFECYCLE);
	}

}
