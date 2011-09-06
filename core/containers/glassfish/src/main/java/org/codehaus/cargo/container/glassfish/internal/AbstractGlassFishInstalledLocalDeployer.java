/*
 * ========================================================================
 *
 * Codehaus CARGO, copyright 2004-2011 Vincent Massol.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ========================================================================
 */
package org.codehaus.cargo.container.glassfish.internal;

import java.util.List;

import org.codehaus.cargo.container.InstalledLocalContainer;
import org.codehaus.cargo.container.configuration.entry.DataSource;
import org.codehaus.cargo.container.deployable.Deployable;
import org.codehaus.cargo.container.deployer.DeployerType;
import org.codehaus.cargo.container.glassfish.GlassFishPropertySet;
import org.codehaus.cargo.container.property.RemotePropertySet;
import org.codehaus.cargo.container.spi.deployer.AbstractLocalDeployer;

/**
 * GlassFish installed local deployer, which uses the GlassFish asadmin to deploy and undeploy
 * applications.
 * 
 * @version $Id$
 */
public abstract class AbstractGlassFishInstalledLocalDeployer extends AbstractLocalDeployer
{

    /**
     * Calls parent constructor, which saves the container.
     * 
     * @param localContainer Container.
     */
    public AbstractGlassFishInstalledLocalDeployer(InstalledLocalContainer localContainer)
    {
        super(localContainer);
    }

    /**
     * Casts the container and returns.
     * 
     * @return Cast container.
     */
    protected AbstractGlassFishInstalledLocalContainer getLocalContainer()
    {
        return (AbstractGlassFishInstalledLocalContainer) super.getContainer();
    }

    /**
     * Casts the configuration and returns.
     * 
     * @return Cast configuration.
     */
    private AbstractGlassFishStandaloneLocalConfiguration getConfiguration()
    {
        return (AbstractGlassFishStandaloneLocalConfiguration)
            this.getLocalContainer().getConfiguration();
    }

    /**
     * {@inheritDoc}
     */
    public DeployerType getType()
    {
        return DeployerType.INSTALLED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deploy(Deployable deployable)
    {
        this.doDeploy(deployable, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void redeploy(Deployable deployable)
    {
        this.doDeploy(deployable, true);
    }

    /**
     * Does the actual deployment.
     * 
     * @param deployable Deployable to deploy.
     * @param overwrite Whether to overwrite.
     */
    protected abstract void doDeploy(Deployable deployable, boolean overwrite);

    /**
     * Deploy a datasource.
     * 
     * @param dataSource Datasource to deploy.
     */
    public abstract void deployDatasource(DataSource dataSource);

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(Deployable deployable)
    {
        super.start(deployable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(Deployable deployable)
    {
        super.stop(deployable);
    }

    /**
     * Returns a filename without its extension.
     * 
     * @param name Filename.
     * @return Filename without its extension.
     */
    protected String cutExtension(String name)
    {
        int idx = name.lastIndexOf('.');
        if (idx >= 0)
        {
            return name.substring(0, idx);
        }
        else
        {
            return name;
        }
    }

    /**
     * Adds connection options.
     * 
     * @param args List to add to.
     */
    protected void addConnectOptions(List<String> args)
    {
        args.add("--interactive=false");
        args.add("--port");
        args.add(this.getConfiguration().getPropertyValue(GlassFishPropertySet.ADMIN_PORT));
        args.add("--user");
        args.add(this.getConfiguration().getPropertyValue(RemotePropertySet.USERNAME));
        args.add("--passwordfile");
        args.add(this.getConfiguration().getPasswordFile().getAbsolutePath());
    }

}