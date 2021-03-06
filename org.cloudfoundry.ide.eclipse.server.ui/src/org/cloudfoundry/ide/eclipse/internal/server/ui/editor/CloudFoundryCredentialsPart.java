/*******************************************************************************
 * Copyright (c) 2012 - 2013 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.ui.editor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryBrandingExtensionPoint;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryPlugin;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryServer;
import org.cloudfoundry.ide.eclipse.internal.server.core.spaces.CloudVersion;
import org.cloudfoundry.ide.eclipse.internal.server.ui.CloudFoundryImages;
import org.cloudfoundry.ide.eclipse.internal.server.ui.CloudFoundryURLNavigation;
import org.cloudfoundry.ide.eclipse.internal.server.ui.CloudUiUtil;
import org.cloudfoundry.ide.eclipse.internal.server.ui.wizards.RegisterAccountWizard;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.wst.server.ui.wizard.IWizardHandle;

/**
 * @author Andy Clement
 * @author Christian Dupuis
 * @author Leo Dos Santos
 * @author Steffen Pingel
 * @author Terry Denney
 * @author Nieraj Singh
 */
public class CloudFoundryCredentialsPart {

	private static final String DEFAULT_DESCRIPTION = "Register or log in to {0} account.";

	private CloudFoundryServer cfServer;

	private Text emailText;

	private TabFolder folder;

	private boolean isFinished;

	private Text passwordText;

	private String serverTypeId;

	private String service;

	private CloudUrlWidget urlWidget;

	private Combo urlCombo;

	private Button validateButton;

	private IWizardHandle wizardHandle;

	private WizardPage wizardPage;

	private Button registerAccountButton;

	private Button cfSignupButton;

	private CloudSpaceChangeHandler spaceChangeHandler;

	private Map<String, CloudVersion> localV2Cache = new HashMap<String, CloudVersion>();

	public CloudFoundryCredentialsPart(CloudFoundryServer cfServer, WizardPage wizardPage) {
		this.cfServer = cfServer;
		this.wizardPage = wizardPage;
		this.serverTypeId = cfServer.getServer().getServerType().getId();
		this.service = CloudFoundryBrandingExtensionPoint.getServiceName(serverTypeId);

		wizardPage.setTitle(NLS.bind("{0} Account", service));
		wizardPage.setDescription(NLS.bind(DEFAULT_DESCRIPTION, service));
		ImageDescriptor banner = CloudFoundryImages.getWizardBanner(serverTypeId);
		if (banner != null) {
			wizardPage.setImageDescriptor(banner);
		}
	}

	public CloudFoundryCredentialsPart(CloudFoundryServer cfServer, IWizardHandle wizardHandle) {
		this.cfServer = cfServer;
		this.wizardHandle = wizardHandle;
		this.serverTypeId = cfServer.getServer().getServerType().getId();
		this.service = CloudFoundryBrandingExtensionPoint.getServiceName(serverTypeId);

		wizardHandle.setTitle(NLS.bind("{0} Account", service));
		wizardHandle.setDescription(NLS.bind(DEFAULT_DESCRIPTION, service));
		ImageDescriptor banner = CloudFoundryImages.getWizardBanner(serverTypeId);
		if (banner != null) {
			wizardHandle.setImageDescriptor(banner);
		}
	}

	public CloudFoundryCredentialsPart(CloudFoundryServer cfServer, WizardPage wizardPage,
			CloudSpaceChangeHandler spaceChangeHandler) {
		this(cfServer, wizardPage);
		this.spaceChangeHandler = spaceChangeHandler;
	}

	public CloudFoundryCredentialsPart(CloudFoundryServer cfServer, IWizardHandle wizardHandle,
			CloudSpaceChangeHandler spaceChangeHandler) {
		this(cfServer, wizardHandle);
		this.spaceChangeHandler = spaceChangeHandler;
	}

	public Composite createComposite(Composite parent) {

		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		folder = new TabFolder(composite, SWT.NONE);
		folder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		folder.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				update(true);
			}
		});

		try {
			createExistingUserComposite(folder);
			update(true);
		}
		catch (Throwable e1) {
			CloudFoundryPlugin.logError(e1);
		}

		return composite;

	}

	public boolean isComplete() {
		return isFinished;
	}

	public void setServer(CloudFoundryServer server) {
		this.cfServer = server;
	}

	protected CloudVersion getLocallyTrackedCloudVersion(String userName, String password, String actualURL) {
		String id = getLocallyTrackedV2ID(userName, password, actualURL);
		return localV2Cache.get(id);
	}

	protected String getLocallyTrackedV2ID(String userName, String password, String actualURL) {
		if (userName == null || password == null || actualURL == null) {
			return null;
		}
		return userName + password + actualURL;
	}

	protected void addLocallyTrackedV2ID(String userName, String password, String actualURL, CloudVersion version) {
		String id = getLocallyTrackedV2ID(userName, password, actualURL);
		if (id != null) {
			localV2Cache.put(id, version);
		}
	}

	/**
	 * Updates V2 spaces ONLY if a previous update was already performed on the
	 * current credentials and URL, and URL is a V2 URL. In such a case, false
	 * is returned indicating that spaces shoudl not be cleared on UI update as
	 * a space is being set. Otherwise, if it is V1 or version cannot be
	 * determined, true is returned indicating that existing spaces in the
	 * server will be cleared.
	 * @param errorMessages
	 * @return
	 */
	protected boolean updateV2SpacesOnUserPasswordChange(List<String> errorMessages) {
		boolean clearV2Spaces = true;
		String url = urlWidget.getURLSelection();
		url = url != null ? CloudUiUtil.getUrlFromDisplayText(url) : null;

		if (url != null) {
			String userName = emailText.getText();
			String password = passwordText.getText();

			CloudVersion version = getLocallyTrackedCloudVersion(userName, password, url);
			clearV2Spaces = updateV2Spaces(errorMessages, userName, password, url, version);

		}
		return clearV2Spaces;
	}

	/**
	 * Update space for given url and credentials ONLY if it is a V2 cloud. If
	 * version is unknown or its V1, this method will indicate to clear existing
	 * spaces.
	 * @param errorMessages
	 * @param userName
	 * @param password
	 * @param url
	 * @param version
	 * @return true if existing spaces should be cleared, if no cloud version is
	 * associated with credentials or url is V1. False if spaces should not be
	 * cleared if it is V2.
	 */
	protected boolean updateV2Spaces(List<String> errorMessages, String userName, String password, String url,
			CloudVersion version) {
		// If it is v2, automatically do a space lookup
		boolean clearV2Spaces = true;
		if (version != null && version.equals(CloudVersion.V2)) {
			clearV2Spaces = updateV2Spaces(errorMessages, userName, password, url);
		}

		return clearV2Spaces;
	}

	protected boolean updateV2Spaces(List<String> errorMessages, String userName, String password, String url) {
		boolean clearV2Spaces = true;
		String errorMsg = null;
		try {
			if (spaceChangeHandler != null && userName != null && password != null && url != null) {
				spaceChangeHandler.updateDescriptor(url, userName, password, getRunnableContext());

				// Update the local cache
				CloudVersion version = CloudUiUtil.getCloudVersion(url, cfServer);
				if (version != null) {
					addLocallyTrackedV2ID(userName, password, url, version);
				}
				// Dont clear the spaces on subsequent update as a new one is
				// being set
				clearV2Spaces = false;
			}

		}
		catch (CoreException e) {
			errorMsg = e.getMessage();
		}
		if (errorMessages != null && errorMsg != null) {
			errorMessages.add(errorMsg);
		}
		return clearV2Spaces;
	}

	protected void updateV2SpacesOnUserPasswordChange() {
		List<String> errorMessages = new ArrayList<String>();
		boolean clearV2Space = updateV2SpacesOnUserPasswordChange(errorMessages);
		update(clearV2Space);
		if (errorMessages.size() > 0) {
			setWizardError(errorMessages.get(0));
		}
	}

	private void createExistingUserComposite(TabFolder folder) {
		Composite composite = new Composite(folder, SWT.NONE);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite topComposite = new Composite(composite, SWT.NONE);
		topComposite.setLayout(new GridLayout(2, false));
		topComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		Label emailLabel = new Label(topComposite, SWT.NONE);
		emailLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		emailLabel.setText("Email:");

		emailText = new Text(topComposite, SWT.BORDER);
		emailText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		emailText.setEditable(true);
		emailText.setFocus();
		if (cfServer.hasValidServerData() && cfServer.getUsername() != null) {
			emailText.setText(cfServer.getUsername());
		}

		emailText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				cfServer.setUsername(emailText.getText());
				updateV2SpacesOnUserPasswordChange();
			}
		});

		Label passwordLabel = new Label(topComposite, SWT.NONE);
		passwordLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		passwordLabel.setText("Password:");

		passwordText = new Text(topComposite, SWT.PASSWORD | SWT.BORDER);
		passwordText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		passwordText.setEditable(true);
		if (cfServer.hasValidServerData() && cfServer.getPassword() != null) {
			passwordText.setText(cfServer.getPassword());
		}

		passwordText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				cfServer.setPassword(passwordText.getText());
				updateV2SpacesOnUserPasswordChange();
			}
		});

		urlWidget = new CloudUrlWidget(cfServer) {

			@Override
			protected void setUpdatedSelectionInServer() {

				super.setUpdatedSelectionInServer();
				List<String> errorMessages = new ArrayList<String>();

				String url = urlWidget.getURLSelection();
				url = url != null ? CloudUiUtil.getUrlFromDisplayText(url) : null;

				boolean clearDescriptor = true;

				if (url != null) {
					String userName = emailText.getText();
					String password = passwordText.getText();

					CloudVersion version = getLocallyTrackedCloudVersion(userName, password, url);

					clearDescriptor = updateV2Spaces(errorMessages, userName, password, url, version);

				}

				// Update button first before setting any error messages to
				// avoid error messages getting wiped
				update(clearDescriptor);

				if (errorMessages.size() > 0) {
					setWizardError(errorMessages.get(0));
				}
			}

		};

		urlWidget.createControls(topComposite);
		urlCombo = urlWidget.getUrlCombo();

		cfServer.setUrl(CloudUiUtil.getUrlFromDisplayText(urlCombo.getItem(urlCombo.getSelectionIndex())));

		final Composite validateComposite = new Composite(composite, SWT.NONE);
		validateComposite.setLayout(new GridLayout(3, false));
		validateComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		validateButton = new Button(validateComposite, SWT.PUSH);
		validateButton.setText("Validate Account");
		validateButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String urlText = urlWidget.getURLSelection();
				String userName = emailText.getText();
				String password = passwordText.getText();

				String errorMsg = CloudUiUtil.validateCredentials(cfServer, userName, password, urlText, true,
						getRunnableContext());

				boolean clearDescriptor = true;

				CloudVersion version = getLocallyTrackedCloudVersion(userName, password, urlText);

				// Only skip checking for spaces if LOCALLY a URL and
				// credentials has been marked as V1
				if (version == null || !version.equals(CloudVersion.V1)) {
					List<String> errorMessages = new ArrayList<String>();

					clearDescriptor = updateV2Spaces(errorMessages, userName, password, urlText);
					if (errorMessages.size() > 0) {
						errorMsg = errorMessages.get(0);
					}
				}

				update(clearDescriptor);

				if (errorMsg == null) {
					setWizardInformation("Account information is valid.");
				}
				else {
					setWizardError(errorMsg);
				}
			}
		});

		registerAccountButton = new Button(validateComposite, SWT.PUSH);
		registerAccountButton.setText("Register Account...");
		registerAccountButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				RegisterAccountWizard wizard = new RegisterAccountWizard(cfServer);
				WizardDialog dialog = new WizardDialog(validateComposite.getShell(), wizard);
				if (dialog.open() == Window.OK) {
					if (wizard.getEmail() != null) {
						emailText.setText(wizard.getEmail());
					}
					if (wizard.getPassword() != null) {
						passwordText.setText(wizard.getPassword());
					}
				}
			}
		});

		cfSignupButton = new Button(validateComposite, SWT.PUSH);
		cfSignupButton.setText("CloudFoundry.com Signup");
		cfSignupButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent event) {
				CloudFoundryURLNavigation.CF_SIGNUP_URL.navigateExternal();
			}
		});

		TabItem item = new TabItem(folder, SWT.NONE);
		item.setText("Account Information");
		item.setControl(composite);
	}

	protected IRunnableContext getRunnableContext() {
		IWizardContainer wizardContainer = getWizardContainer();
		if (wizardContainer != null) {
			return wizardContainer;
		}
		else if (wizardHandle != null) {
			return new IRunnableContext() {
				public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable)
						throws InvocationTargetException, InterruptedException {
					wizardHandle.run(fork, cancelable, runnable);
				}
			};
		}
		else {
			return PlatformUI.getWorkbench().getProgressService();
		}
	}

	private IWizardContainer getWizardContainer() {
		if (wizardPage != null && wizardPage.getWizard() != null && wizardPage.getWizard().getContainer() != null) {
			return wizardPage.getWizard().getContainer();
		}
		return null;
	}

	private void setWizardDescription(String message) {
		if (wizardHandle != null) {
			wizardHandle.setDescription(message);
		}
		else if (wizardPage != null) {
			wizardPage.setDescription(message);
		}
	}

	private void setWizardError(String message) {
		if (wizardHandle != null) {
			wizardHandle.setMessage(message, DialogPage.ERROR);
		}
		else if (wizardPage != null) {
			wizardPage.setErrorMessage(message);
		}
	}

	private void setWizardInformation(String message) {
		if (wizardHandle != null) {
			wizardHandle.setMessage(message, DialogPage.INFORMATION);
		}
		else if (wizardPage != null) {
			wizardPage.setMessage(message, DialogPage.INFORMATION);
		}
	}

	private void update(boolean clearSpaceDescriptor) {
		isFinished = true;
		setWizardError(null);

		// CF signup is only available for VMware CF
		String selection = urlWidget.getURLSelection();
		if (CloudFoundryURLNavigation.canEnableCloudFoundryNavigation(selection)) {
			cfSignupButton.setVisible(true);
		}
		else {
			cfSignupButton.setVisible(false);
		}

		if (clearSpaceDescriptor && spaceChangeHandler != null) {
			// Clear existing space
			spaceChangeHandler.clearDescriptor();
		}

		if (folder.getSelectionIndex() == 0) {
			String message = "";
			isFinished = false;

			if (emailText.getText() == null || emailText.getText().length() == 0) {
				message = "Enter an email address.";
			}
			else if (passwordText.getText() == null || passwordText.getText().length() == 0) {
				message = "Enter a password.";
			}
			else if (urlCombo.getSelectionIndex() < 0) {
				message = NLS.bind("Select a {0} URL.", service);
			}
			else {
				isFinished = true;
				message = NLS.bind(DEFAULT_DESCRIPTION, service);
			}
			validateButton.setEnabled(isFinished);
			registerAccountButton.setEnabled(CloudFoundryBrandingExtensionPoint.supportsRegistration(serverTypeId,
					urlCombo.getText()));
			setWizardDescription(message);

			if (wizardHandle != null) {
				wizardHandle.update();
			}
			else if (getWizardContainer() != null && getWizardContainer().getCurrentPage() != null) {
				getWizardContainer().updateButtons();
			}
		}
		else if (folder.getSelectionIndex() == 1) {
			setWizardDescription(NLS.bind("Create a new {0} account, then switch to Enter Credentials tab to log in.",
					service));
		}
	}

}
