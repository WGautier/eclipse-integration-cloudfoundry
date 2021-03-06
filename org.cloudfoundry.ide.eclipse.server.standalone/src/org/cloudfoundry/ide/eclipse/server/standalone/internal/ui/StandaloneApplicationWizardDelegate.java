/*******************************************************************************
 * Copyright (c) 2013 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.server.standalone.internal.ui;

import java.util.ArrayList;
import java.util.List;

import org.cloudfoundry.ide.eclipse.internal.server.core.ApplicationModule;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryServer;
import org.cloudfoundry.ide.eclipse.internal.server.core.ValueValidationUtil;
import org.cloudfoundry.ide.eclipse.internal.server.ui.wizards.AbstractApplicationWizardDelegate;
import org.cloudfoundry.ide.eclipse.internal.server.ui.wizards.ApplicationWizardDescriptor;
import org.cloudfoundry.ide.eclipse.internal.server.ui.wizards.CloudFoundryApplicationServicesWizardPage;
import org.eclipse.jface.wizard.IWizardPage;

public class StandaloneApplicationWizardDelegate extends
		AbstractApplicationWizardDelegate {

	public StandaloneApplicationWizardDelegate() {
	}

	public List<IWizardPage> getWizardPages(
			ApplicationWizardDescriptor descriptor,
			CloudFoundryServer cloudServer, ApplicationModule applicationModule) {
		List<IWizardPage> defaultPages = new ArrayList<IWizardPage>();

		StandaloneDeploymentWizardPage deploymentPage = new StandaloneDeploymentWizardPage(
				cloudServer, applicationModule, descriptor);

		StandaloneApplicationWizardPage runtimeFrameworkPage = new StandaloneApplicationWizardPage(
				cloudServer, deploymentPage, applicationModule, descriptor);

		defaultPages.add(runtimeFrameworkPage);

		defaultPages.add(deploymentPage);

		CloudFoundryApplicationServicesWizardPage servicesPage = new CloudFoundryApplicationServicesWizardPage(
				cloudServer, applicationModule, descriptor);

		defaultPages.add(servicesPage);
		return defaultPages;

	}

	public boolean isValid(ApplicationWizardDescriptor applicationDescriptor) {

		return super.isValid(applicationDescriptor)
				&& applicationDescriptor.getStaging() != null
				&& !ValueValidationUtil.isEmpty(applicationDescriptor
						.getStaging().getCommand());
	}
}
