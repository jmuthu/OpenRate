/* ====================================================================
 * Limited Evaluation License:
 *
 * This software is open source, but licensed. The license with this package
 * is an evaluation license, which may not be used for productive systems. If
 * you want a full license, please contact us.
 *
 * The exclusive owner of this work is the OpenRate project.
 * This work, including all associated documents and components
 * is Copyright of the OpenRate project 2006-2013.
 *
 * The following restrictions apply unless they are expressly relaxed in a
 * contractual agreement between the license holder or one of its officially
 * assigned agents and you or your organisation:
 *
 * 1) This work may not be disclosed, either in full or in part, in any form
 *    electronic or physical, to any third party. This includes both in the
 *    form of source code and compiled modules.
 * 2) This work contains trade secrets in the form of architecture, algorithms
 *    methods and technologies. These trade secrets may not be disclosed to
 *    third parties in any form, either directly or in summary or paraphrased
 *    form, nor may these trade secrets be used to construct products of a
 *    similar or competing nature either by you or third parties.
 * 3) This work may not be included in full or in part in any application.
 * 4) You may not remove or alter any proprietary legends or notices contained
 *    in or on this work.
 * 5) This software may not be reverse-engineered or otherwise decompiled, if
 *    you received this work in a compiled form.
 * 6) This work is licensed, not sold. Possession of this software does not
 *    imply or grant any right to you.
 * 7) You agree to disclose any changes to this work to the copyright holder
 *    and that the copyright holder may include any such changes at its own
 *    discretion into the work
 * 8) You agree not to derive other works from the trade secrets in this work,
 *    and that any such derivation may make you liable to pay damages to the
 *    copyright holder
 * 9) You agree to use this software exclusively for evaluation purposes, and
 *    that you shall not use this software to derive commercial profit or
 *    support your business or personal activities.
 *
 * This software is provided "as is" and any expressed or impled warranties,
 * including, but not limited to, the impled warranties of merchantability
 * and fitness for a particular purpose are disclaimed. In no event shall
 * Tiger Shore Management or its officially assigned agents be liable to any
 * direct, indirect, incidental, special, exemplary, or consequential damages
 * (including but not limited to, procurement of substitute goods or services;
 * Loss of use, data, or profits; or any business interruption) however caused
 * and on theory of liability, whether in contract, strict liability, or tort
 * (including negligence or otherwise) arising in any way out of the use of
 * this software, even if advised of the possibility of such damage.
 * This software contains portions by The Apache Software Foundation, Robert
 * Half International.
 * ====================================================================
 */

package OpenRate.resource;

import OpenRate.OpenRate;
import OpenRate.exception.InitializationException;
import OpenRate.logging.LogFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * The ResourceContext class is a pseudo InitialContext. It is
 * responsible for servicing lookups from the application classes
 * to a set of the ResourceFactories. It creates & initializes
 * the Factories during application startup, and call the Factory
 * cleanup methods during application shutdown.
 *
 */
public class ResourceContext
{
  private static HashMap<String, IResource> resourceMap = new HashMap<>();
  private boolean active;

 /**
  * Default Constructor
  */
  public ResourceContext()
  {
    // no op
  }

 /**
  * Get the keyset of all the resources in the map
  *
  * @return The Keyset of the resources in the map
  */
  public Collection<String> keySet()
  {
    return resourceMap.keySet();
  }

 /**
  * Manually register a resource with the ResourceContext object. The
  * resource should have already been initialized.
  *
  * @param name The resource name
  * @param resource The resource object reference
  */
  public void register(String name, IResource resource) throws InitializationException
  {
    if (name == null || name.isEmpty())
    {
      throw new InitializationException("Resource name is empty for resource <" + resource.toString() + ">", "resourceContext");
    }
    
    if (resource == null)
    {
      throw new InitializationException("Resource is null for resource <" + name + ">", "resourceContext");
    }
    
    resourceMap.put(name, resource);
    
    // set that we are active
    active = true;
  }

 /**
  * Lookup a resource by name. If found, return to caller. Else
  * return null.
  *
  * @param name The resource to look up
  * @return The resource object
  */
  public IResource get(String name)
  {
    return resourceMap.get(name);
  }

 /**
  * Perform whatever cleanup is required of the underlying object. We take two
  * goes at this, shutting down all non-log resources first, then the log last.
  * 
  * The reason for this this that we might need the log right up until the very
  * last minute.
  */
  public void cleanup()
  {
    Collection<IResource> resources = resourceMap.values();
    Iterator<IResource>   iter      = resources.iterator();

    IResource logResource = null;
    
    while (iter.hasNext())
    {
      IResource resource = iter.next();
      
      if (resource.getSymbolicName() == null)
      {
        System.err.println("Resource name is null for resource <" + resource.toString() + ">");
      }
      
      System.out.println("Closing <" + resource.getSymbolicName() + ">");
      
      if (resource.getSymbolicName().equals(LogFactory.RESOURCE_KEY))
      {
        logResource = resource;
      }
      else
      {
        resource.close();
      }
    }
    
    // Now do the log resource
    if (logResource != null)
      logResource.close();

    // Clear down the map
    resourceMap.clear();

    // set that we are no longer active
    active = false;
  }

  /**
   * @return the active
   */
  public boolean isActive() {
    return active;
  }

  /**
   * @param active the active to set
   */
  public void setActive(boolean active) {
    this.active = active;
  }
}
