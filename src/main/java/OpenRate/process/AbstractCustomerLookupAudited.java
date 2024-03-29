/* ====================================================================
 * Limited Evaluation License:
 *
 * This software is open source, but licensed. The license with this package
 * is an evaluation license, which may not be used for productive systems. If
 * you want a full license, please contact us.
 *
 * The exclusive owner of this work is the OpenRate project.
 * This work, including all associated documents and components
 * is Copyright of the OpenRate project 2006-2014.
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
 * The OpenRate Project or its officially assigned agents be liable to any
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

package OpenRate.process;

import OpenRate.cache.CustomerCacheAudited;
import OpenRate.cache.ICacheManager;
import OpenRate.configurationmanager.ClientManager;
import OpenRate.exception.InitializationException;
import OpenRate.exception.ProcessingException;
import OpenRate.lang.AuditSegment;
import OpenRate.lang.ProductList;
import OpenRate.logging.LogUtil;
import OpenRate.record.IRecord;
import OpenRate.resource.CacheFactory;
import OpenRate.utils.PropertyUtils;

/**
 * This class is a somewhat degenerate example of a Filter, but it does show how
 * one would write a Filter. A filter implements a doWork() method that is
 * passed a set of records from the inbound channel. The Filter should process
 * the provided records and return the output records as a Collection. The
 * Filter may transform the records in-place, or it may toss the original
 * records and create completely new ones. It's entirely up to the Filter.
 */
public abstract class AbstractCustomerLookupAudited
    extends AbstractPlugIn
{
  private ICacheManager CM;

   /**
    * The reference to the customer cache, so we can access the methods in it
    */
  protected CustomerCacheAudited CC;

  // Used to update the cache with new information
  private final static String SERVICE_CACHE_UPDATE  = "UpdateCache";

  // -----------------------------------------------------------------------------
  // ------------------ Start of initialisation functions ------------------------
  // -----------------------------------------------------------------------------

 /**
  * Initialise the module. Called during pipeline creation to initialise:
  *  - Configuration properties that are defined in the properties file.
  *  - The references to any cache objects that are used in the processing
  *  - The symbolic name of the module
  *
  * @param PipelineName The name of the pipeline this module is in
  * @param ModuleName The name of this module in the pipeline
  * @throws OpenRate.exception.InitializationException
  */
  @Override
  public void init(String PipelineName, String ModuleName)
    throws InitializationException
  {
    String CacheObjectName;

    // Register ourself with the client manager
    setSymbolicName(ModuleName);

    // do the inherited initialisation
    super.init(PipelineName,ModuleName);

    // Get the cache object reference
    CacheObjectName = PropertyUtils.getPropertyUtils().getPluginPropertyValue(PipelineName,
                                                           ModuleName,
                                                           "DataCache");

    // Load up the customer information held in the Cached Object
    CM = CacheFactory.getGlobalManager(CacheObjectName);

    if (CM == null)
    {
      message = "Could not find cache entry for <" + CacheObjectName + ">";
      throw new InitializationException(message,getSymbolicName());
    }

    // Load up the mapping arrays
    CC = (CustomerCacheAudited)CM.get(CacheObjectName);

    if (CC == null)
    {
      message = "Could not find cache entry for <" + CacheObjectName + ">";
      throw new InitializationException(message,getSymbolicName());
    }
  }

  // -----------------------------------------------------------------------------
  // ------------------ Start of inherited Plug In functions ---------------------
  // -----------------------------------------------------------------------------

 /**
  * This is called when the synthetic Header record is encountered, and has the
  * meaning that the stream is starting.
  */
  @Override
  public IRecord procHeader(IRecord r)
  {
    // check to see if we have to perform the dynamic update or not
    CC.checkUpdate();

    return r;
  }

 /**
  * This is called when a stream trailer record is encountered, and has the
  * meaning that the stream is ending
  */
  @Override
  public IRecord procTrailer(IRecord r)
  {
    return r;
  }

// -----------------------------------------------------------------------------
// ------------- Start of inherited IEventInterface functions ------------------
// -----------------------------------------------------------------------------

 /**
  * registerClientManager registers this class as a client of the ECI listener
  * and publishes the commands that the plug in understands. The listener is
  * responsible for delivering only these commands to the plug in.
  *
  */
  @Override
  public void registerClientManager() throws InitializationException
  {
    //Register this Client
    ClientManager.getClientManager().registerClient(getPipeName(),getSymbolicName(), this);

    //Register services for this Client
    ClientManager.getClientManager().registerClientService(getSymbolicName(), SERVICE_CACHE_UPDATE, ClientManager.PARAM_DYNAMIC);
  }

 /**
  * processControlEvent is the event processing hook for the External Control
  * Interface (ECI). This allows interaction with the external world, for
  * example turning the dumping on and off.
  *
  * @param Command The command that we are to work on
  * @param Init True if the pipeline is currently being constructed
  * @param Parameter The parameter value for the command
  * @return The result message of the operation
  */
  @Override
  public String processControlEvent(String Command, boolean Init, String Parameter)
  {
    int ResultCode = -1;

    // Set the batch size
    if (Command.equalsIgnoreCase(SERVICE_CACHE_UPDATE))
    {
      if (Parameter.equalsIgnoreCase("true"))
      {
        checkUpdate();
        ResultCode = 0;
      }
    }

    if (ResultCode == 0)
    {
      getPipeLog().debug(LogUtil.LogECIPipeCommand(getSymbolicName(), getPipeName(), Command, Parameter));

      return "OK";
    }
    else
    {
      return super.processControlEvent(Command, Init, Parameter);
    }
  }

  // -----------------------------------------------------------------------------
  // ------------------------ Start of custom functions --------------------------
  // -----------------------------------------------------------------------------

 /**
  * This returns the internal customer account ID based on a login, and the date
  * of the CDR.
  *
  * @param Login The Login (Alias) used to identify the account
  * @param CDRDate The date of the CDR
  * @return The Customer ID associated with the login at the date
  * @throws ProcessingException
  */
  protected Integer getCustId(String Login, long CDRDate) throws ProcessingException
  {
    Integer tmpCustId;

    tmpCustId = CC.getCustId(Login,CDRDate);

    return tmpCustId;
  }

 /**
  * This returns the internal alias record based on a login, and the date
  * of the CDR.
  *
  * @param Login The Login (Alias) used to identify the account
  * @param CDRDate The date of the CDR
  * @return The Alias record associated with the login at the date
  * @throws ProcessingException
  */
  protected String getSubscriptionId(String Login, long CDRDate) throws ProcessingException
  {
    return CC.getSubscriptionId(Login,CDRDate);
  }

 /**
  * This returns a product list based on a login, a service and the validity of
  * the customer and of the individual products.
  *
  * @param CustID The internal customer account ID to recover the MSN for
  * @return The MSN (external customer account ID)
  * @throws ProcessingException
  */
  protected String getMSN(Integer CustID) throws ProcessingException
  {
    String tmpMSN;

    tmpMSN = CC.getExtCustID(CustID);

    return tmpMSN;
  }

 /**
  * Gets the audit segment for the CustId and the CDR date.
  *
  * @param custId The internal customer account AuditSegID to retrive the audit seg for
  * @param CDRDate The date to retrieve for
  * @return The audit segment for the account and date
  */
  public AuditSegment getAuditSegment(Integer custId, long CDRDate)
  {
    AuditSegment tmpAuditSegment;

    //get the correct audit segment
    tmpAuditSegment = CC.getAuditSegment(custId, CDRDate);

    return tmpAuditSegment;
  }

 /**
  * This returns a product list based on a login, a service and the validity of
  * the customer and of the individual products.
  *
  * @param Login The Login (Alias) used to identify the account
  * @param CDRDate The date of the CDR
  * @return The list of products for the login-date-service combination
  * @throws ProcessingException
  */
  protected ProductList getProductList(String Login, long CDRDate) throws ProcessingException
  {
    ProductList tmpProductList;

    tmpProductList = CC.getProducts(Login,CDRDate);

    return tmpProductList;
  }

 /**
  * This returns a product list based on an already identified audit segment,
  * plus the Subscription Id to search for. This is faster for multiple accesses,
  * as the real hard work is in locating the correct audit segment to use.
  *
  * @param tmpAuditSeg the audit segment to get the product list from
  * @param SubId the subscription id to recover products for, or null for all
  * @return The list of products for the login-date-service combination
  * @throws ProcessingException
  */
  protected ProductList getProductList(AuditSegment tmpAuditSeg, String SubId) throws ProcessingException
  {
    ProductList tmpProductList;

    tmpProductList = CC.getProducts(tmpAuditSeg, SubId);

    return tmpProductList;
  }

 /**
  * Return the value of the ERA associated with an account.
  *
  * @param Login The login to get the ERA for
  * @param EraKey The ERA key to get
  * @param CDRDate The date of the CDR
  * @return The ERA value
  * @throws ProcessingException
  */
  public String getERA(String Login, String EraKey, long CDRDate) throws ProcessingException
  {
    String tmpERAValue;

    tmpERAValue = CC.getERA(Login, EraKey, CDRDate);

    return tmpERAValue;
  }

 /**
  * Return the value of the ERA associated with an account.
  *
  * @param tmpAuditSeg the audit segment to get the product list from
  * @param eraKey the ERA key to look for
  * @return The ERA value
  */
  public String getERA(AuditSegment tmpAuditSeg, String eraKey)
  {
    String tmpERAValue;

    tmpERAValue = CC.getERA(tmpAuditSeg, eraKey);

    return tmpERAValue;
  }

 /**
  * Check if there are any data updates to do - to be called explicitly in the
  * case of non-transactional processing. (Transactional processing calls
  * on each transaction start).
  */
  public void checkUpdate()
  {
    // check to see if we have to perform the dynamic update or not
    CC.checkUpdate();
  }
}
