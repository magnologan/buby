package burp;

import burp.*;

import org.jruby.*;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.ThreadContext; 
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.RubyBoolean;
import java.util.List;
import javax.swing.JMenuItem;

/**
 * This is an implementation of the BurpExtender/IBurpExtender interface
 * for Burp Suite which provides glue between a Ruby runtime and Burp.
 *
 * This is a complete implementation of the Burp Extender interfaces available
 * as of Burp Suite 1.4
 */
public class BurpExtender implements IBurpExtender, IExtensionStateListener, IHttpListener, IProxyListener, IScannerListener, IContextMenuFactory, IScopeChangeListener { 

    // Legacy callbacks
    public final static String L_CLOSE_METH     = "evt_application_closing";
    public final static String L_HTTPMSG_METH   = "evt_http_message";
    public final static String L_INIT_METH      = "evt_extender_init";
    public final static String L_MAINARGS_METH  = "evt_commandline_args";
    public final static String L_PROXYMSG_METH  = "evt_proxy_message_raw";
    public final static String L_SCANISSUE_METH = "evt_scan_issue";
    public final static String L_REG_METH       = "evt_register_callbacks";

    // new style callbacks
    public final static String INIT_METH        = "extender_initialize";
    public final static String REG_METH         = "register_callbacks";
    public final static String PROXYMSG_METH    = "process_proxy_message";
    public final static String HTTPMSG_METH     = "process_http_messge";
    public final static String SCANISSUE_METH   = "new_scan_issue";

    // new callback methods
    public final static String UNLOAD_METH      = "extension_unloaded";
    public final static String MENUFAC_METH     = "create_menu_items";
    public final static String SCOPE_METH       = "scope_changed";
    

    // Flag used to identify Burp Suite as a whole.
    public static final int TOOL_SUITE = 0x00000001;
    // Flag used to identify the Burp Target tool.
    public static final int TOOL_TARGET = 0x00000002;
    // Flag used to identify the Burp Proxy tool.
    public static final int TOOL_PROXY = 0x00000004;
    // Flag used to identify the Burp Spider tool.
    public static final int TOOL_SPIDER = 0x00000008;
    // Flag used to identify the Burp Scanner tool.
    public static final int TOOL_SCANNER = 0x00000010;
    // Flag used to identify the Burp Intruder tool.
    public static final int TOOL_INTRUDER = 0x00000020;
    // Flag used to identify the Burp Repeater tool.
    public static final int TOOL_REPEATER = 0x00000040;
    // Flag used to identify the Burp Sequencer tool.
    public static final int TOOL_SEQUENCER = 0x00000080;
    // Flag used to identify the Burp Decoder tool.
    public static final int TOOL_DECODER = 0x00000100;
    // Flag used to identify the Burp Comparer tool.
    public static final int TOOL_COMPARER = 0x00000200;
    // Flag used to identify the Burp Extender tool.
    public static final int TOOL_EXTENDER = 0x00000400;

    // Internal reference to hold the ruby Burp handler
    private static IRubyObject r_obj = null;

    /**
     * Sets an internal reference to the ruby handler class or module to use 
     * for proxied BurpExtender events into a ruby runtime.
     *
     * Generally, this should probably be called before burp.StartBurp.main. 
     * However, it is also possible to set this afterwards and even swap in 
     * new objects during runtime.
     */
    public static void setHandler(IRubyObject hnd) { r_obj = hnd; }

    /** 
     * Returns the internal Ruby handler reference. 
     *
     * The handler is the ruby class or module used for proxying BurpExtender 
     * events into a ruby runtime.
     */
    public static IRubyObject getHandler() { return r_obj; }


    /** 
     * This constructor is invoked from Burp's extender framework.
     *
     * This implementation invokes the <code>INIT_METH</code> method
     * from the Ruby handler object if one is defined passing it Ruby
     * usable reference to the instance.
     *
     */
    public BurpExtender() {
      if (r_obj !=null && r_obj.respondsTo(INIT_METH))
        r_obj.callMethod(ctx(r_obj), INIT_METH, to_ruby(rt(r_obj), this));
      if (r_obj !=null && r_obj.respondsTo(L_INIT_METH))
        r_obj.callMethod(ctx(r_obj), L_INIT_METH, to_ruby(rt(r_obj), this));
    }


    /**
     * This method is invoked immediately after the implementation's constructor
     * to pass any command-line arguments that were passed to Burp Suite on
     * startup. 
     *
     * This implementation invokes the method defined by 
     * <code>L_MAINARGS_METH</code> in the Ruby handler if both the handler
     * and its ruby method are defined.
     *
     * It allows Ruby implementations to control aspects of their behaviour at 
     * runtime by defining their own command-line arguments.
     *
     * WARNING: Burp appears to have a bug (as of 1.2 and 1.2.05) which causes
     * Burp to exit immediately if arguments are supplied regardless whether 
     * this handler is used.
     *
     * @param args The command-line arguments passed to Burp Suite on startup.
     */
    public void setCommandLineArgs(String[] args) {
      if(r_obj != null && r_obj.respondsTo(L_MAINARGS_METH))
        r_obj.callMethod(ctx(r_obj), L_MAINARGS_METH, to_ruby(rt(r_obj), args));
    }
  
    /**
     * This method is invoked on startup. It registers an instance of the 
     * <code>burp.IBurpExtenderCallbacks</code> interface, providing methods 
     * that may be invoked by the implementation to perform various actions.
     * 
     * The call to registerExtenderCallbacks need not return, and 
     * implementations may use the invoking thread for any purpose.<p>
     *
     * This implementation simply passes a ruby-usable "callbacks" instance to 
     * the Ruby handler using the method defined by <code>REG_METH</code> if 
     * both the handler and its ruby method are defined.
     *
     * @param callbacks An implementation of the 
     * <code>IBurpExtenderCallbacks</code> interface.
     */
    public void registerExtenderCallbacks(IBurpExtenderCallbacks cb) {
      cb.setExtensionName("Buby");
      cb.issueAlert("[BurpExtender] registering JRuby handler callbacks");
      cb.registerExtensionStateListener(this);
      cb.registerHttpListener(this);
      cb.registerScannerListener(this);
      cb.registerContextMenuFactory(this);
      cb.registerScopeChangeListener(this);
      if(r_obj != null) {
        boolean respondsLegacyRegister = r_obj.respondsTo(L_REG_METH);
        boolean respondsRegister = r_obj.respondsTo(REG_METH);

        IRubyObject args[] = {to_ruby(rt(r_obj), cb), RubyBoolean.newBoolean(rt(r_obj), false)};
        if(respondsLegacyRegister && respondsRegister){
          r_obj.callMethod(ctx(r_obj), REG_METH, args[0]);
          r_obj.callMethod(ctx(r_obj), L_REG_METH, args);
        } else if(respondsRegister){
          r_obj.callMethod(ctx(r_obj), REG_METH, args[0]);
        } else if(respondsLegacyRegister)
          r_obj.callMethod(ctx(r_obj), L_REG_METH, args[0]);
      }
    }

    /**
     * This method is invoked by Burp Proxy whenever a client request or server
     * response is received. 
     *
     * This implementation simply passes all arguments to the Ruby handler's 
     * method defined by <code>L_PROXYMSG_METH</code> if both the handler and
     * its ruby method are defined.
     *
     * This allows Ruby implementations to perform logging functions, modify 
     * the message, specify an action (intercept, drop, etc.) and perform any 
     * other arbitrary processing.
     *
     * @param messageReference An identifier which is unique to a single 
     * request/response pair. This can be used to correlate details of requests
     * and responses and perform processing on the response message accordingly.
     * @param messageIsRequest Flags whether the message is a client request or
     * a server response.
     * @param remoteHost The hostname of the remote HTTP server.
     * @param remotePort The port of the remote HTTP server.
     * @param serviceIsHttps Flags whether the protocol is HTTPS or HTTP.
     * @param httpMethod The method verb used in the client request.
     * @param url The requested URL.
     * @param resourceType The filetype of the requested resource, or a 
     * zero-length string if the resource has no filetype.
     * @param statusCode The HTTP status code returned by the server. This value
     * is <code>null</code> for request messages.
     * @param responseContentType The content-type string returned by the 
     * server. This value is <code>null</code> for request messages.
     * @param message The full HTTP message.
     * @param action An array containing a single integer, allowing the
     * implementation to communicate back to Burp Proxy a non-default 
     * interception action for the message. The default value is 
     * <code>ACTION_FOLLOW_RULES</code>. Set <code>action[0]</code> to one of 
     * the other possible values to perform a different action.
     * @return Implementations should return either (a) the same object received
     * in the <code>message</code> paramater, or (b) a different object 
     * containing a modified message.
     */
    @Deprecated
    public byte[] processProxyMessage( 
        int messageReference, 
        boolean messageIsRequest, 
        String remoteHost, 
        int remotePort, 
        boolean serviceIsHttps, 
        String httpMethod, 
        String url, 
        String resourceType, 
        String statusCode, 
        String responseContentType, 
        byte[] message, 
        int[] action ) 
    {

      if (r_obj != null && r_obj.respondsTo(L_PROXYMSG_METH)) {
        Ruby rt = rt(r_obj);
        // prepare an alternate action value to present to ruby
        IRubyObject r_action = to_ruby(rt, action);

        // prepare an alternate String message value to present to ruby
        //String message_str = new String(message);
        IRubyObject r_msg = to_ruby(rt, message);

        IRubyObject pxy_msg[] = {
          to_ruby(rt, messageReference),
          to_ruby(rt, messageIsRequest),
          to_ruby(rt, remoteHost),
          to_ruby(rt, remotePort),
          to_ruby(rt, serviceIsHttps),
          to_ruby(rt, httpMethod),
          to_ruby(rt, url),
          to_ruby(rt, resourceType),
          to_ruby(rt, statusCode),
          to_ruby(rt, responseContentType),
          r_msg,
          r_action
        };

        // slurp back in the action value in-case it's been changed
        action[0] = ((int[])r_action.toJava(int[].class))[0];

        IRubyObject ret = r_obj.callMethod(ctx(r_obj), L_PROXYMSG_METH, pxy_msg);
        if(ret != r_msg) {
          return (byte []) ret.toJava(byte[].class);
        }
      }

      return message;
    }

    /**
     * This method is invoked when an HTTP message is being processed by the
     * Proxy.
     *
     * This method corresponds with Buby#process_proxy_message
     *
     * @param messageIsRequest Indicates whether the HTTP message is a request
     * or a response.
     * @param message An
     * <code>IInterceptedProxyMessage</code> object that extensions can use to
     * query and update details of the message, and control whether the message
     * should be intercepted and displayed to the user for manual review or
     * modification.
     */
    public void processProxyMessage(boolean messageIsRequest, IInterceptedProxyMessage message)
    {
      if (r_obj != null && r_obj.respondsTo(PROXYMSG_METH)) {
        Ruby rt = rt(r_obj);
        IRubyObject http_msg[] = {
          to_ruby(rt, messageIsRequest),
          to_ruby(rt, message)
        };
        r_obj.callMethod(ctx(r_obj), PROXYMSG_METH, http_msg);
      }
    }

    /** 
     * Added in Burp 1.2.09 
     * @note Changed in Burp 1.5.01+
     * No javadoc yet but here's what the PortSwigger dev blog has to say:
     *
     * The processHttpMessage method is invoked whenever any of Burp's tools 
     * makes an HTTP request or receives a response. This is effectively a 
     * generalised version of the existing processProxyMessage method, and 
     * can be used to intercept and modify the HTTP traffic of all Burp 
     * tools.
     */
    @Deprecated
    public void processHttpMessage(
        String toolName, 
        boolean messageIsRequest, 
        IHttpRequestResponse messageInfo ) 
    {
      if (r_obj != null && r_obj.respondsTo(L_HTTPMSG_METH)) {
        Ruby rt = rt(r_obj);
        IRubyObject http_msg[] = {
          to_ruby(rt, toolName),
          to_ruby(rt, messageIsRequest),
          to_ruby(rt, messageInfo)
        };
    
        r_obj.callMethod(ctx(r_obj), L_HTTPMSG_METH, http_msg);
      }
    }

     /**
     * @note This is the 1.5.01+ version of this callback
      * This method is invoked when an HTTP request is about to be issued, and
      * when an HTTP response has been received.
      *
      * @param toolFlag A flag indicating the Burp tool that issued the request.
      * Burp tool flags are defined in the
      * <code>IBurpExtenderCallbacks</code> interface.
      * @param messageIsRequest Flags whether the method is being invoked for a
      * request or response.
      * @param messageInfo Details of the request / response to be processed.
      * Extensions can call the setter methods on this object to update the
      * current message and so modify Burp's behavior.
      */
     public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo)
    {
      if (r_obj != null && r_obj.respondsTo(HTTPMSG_METH)) {
        Ruby rt = rt(r_obj);
        IRubyObject http_msg[] = {
          to_ruby(rt, toolFlag),
          to_ruby(rt, messageIsRequest),
          to_ruby(rt, messageInfo)
        };

        r_obj.callMethod(ctx(r_obj), HTTPMSG_METH, http_msg);
      }
    }

    /** 
     * Added in Burp 1.2.09 
     *
     * The newScanIssue method is invoked whenever Burp Scanner discovers a 
     * new, unique issue, and can be used to perform customised reporting or 
     * logging of issues.
     */
    public void newScanIssue(IScanIssue issue) {
      if (r_obj != null && r_obj.respondsTo(SCANISSUE_METH))
        r_obj.callMethod(ctx(r_obj), SCANISSUE_METH, to_ruby(rt(r_obj), issue));
      if (r_obj != null && r_obj.respondsTo(L_SCANISSUE_METH))
        r_obj.callMethod(ctx(r_obj), L_SCANISSUE_METH, to_ruby(rt(r_obj), issue));
    }


    /**
     * This method is invoked immediately before Burp Suite exits. 
     * This implementation simply invokes the Ruby handler's method defined
     * by <code>L_CLOSE_METH</code> if both the handler and its ruby method are
     * defined.
     *
     * This allows implementations to carry out any clean-up actions necessary
     * (e.g. flushing log files or closing database resources, etc.).
     */
    public void applicationClosing() {
      if (r_obj != null && r_obj.respondsTo(L_CLOSE_METH))
        r_obj.callMethod(ctx(r_obj), L_CLOSE_METH);
    }

    // Private method to return the ThreadContext for a given ruby object.
    // This is used in the various event proxies
    private ThreadContext ctx(IRubyObject obj) {
      return rt(obj).getThreadService().getCurrentContext();
    }

    // Private method to return the ruby runtime for a given ruby object.
    // This is used in the various event proxies
    private Ruby rt(IRubyObject obj) {
      return obj.getRuntime();
    }

    // private method to transfer arbitrary java objects into a ruby runtime.
    // This is used in the various event proxies to pass arguments to the
    // ruby handler object.
    private IRubyObject to_ruby(Ruby rt, Object obj) {
      return JavaUtil.convertJavaToUsableRubyObject(rt, obj);
    }

    /** 
     * Causes Burp Proxy to follow the current interception rules to determine
     * the appropriate action to take for the message.
     */
    public final static int ACTION_FOLLOW_RULES = 0;

    /** 
     * Causes Burp Proxy to present the message to the user for manual
     * review or modification.
     */
    public final static int ACTION_DO_INTERCEPT = 1;

    /** 
     * Causes Burp Proxy to forward the message to the remote server or client.
     */
    public final static int ACTION_DONT_INTERCEPT = 2;

    /** 
     * Causes Burp Proxy to drop the message and close the client connection.
     */
    public final static int ACTION_DROP = 3;    

    /**
     * Causes Burp Proxy to follow the current interception rules to determine
     * the appropriate action to take for the message, and then make a second
     * call to processProxyMessage.
     */
    public final static int ACTION_FOLLOW_RULES_AND_REHOOK = 0x10;
    /**
     * Causes Burp Proxy to present the message to the user for manual
     * review or modification, and then make a second call to
     * processProxyMessage.
     */
    public final static int ACTION_DO_INTERCEPT_AND_REHOOK = 0x11;
    /**
     * Causes Burp Proxy to skip user interception, and then make a second call
     * to processProxyMessage.
     */
    public final static int ACTION_DONT_INTERCEPT_AND_REHOOK = 0x12;

    /**
     * Extensions can implement this interface and then call
     * <code>IBurpExtenderCallbacks.registerExtensionStateListener()</code> to
     * register an extension state listener. The listener will be notified of
     * changes to the extension's state. <b>Note:</b> Any extensions that start
     * background threads or open system resources (such as files or database
     * connections) should register a listener and terminate threads / close
     * resources when the extension is unloaded.
     */
    public void extensionUnloaded() {
      if (r_obj != null && r_obj.respondsTo(UNLOAD_METH))
        r_obj.callMethod(ctx(r_obj), UNLOAD_METH);
    }

    /**
     * This method will be called by Burp when the user invokes a context menu
     * anywhere within Burp. The factory can then provide any custom context
     * menu items that should be displayed in the context menu, based on the
     * details of the menu invocation.
     *
     * @param invocation An object that implements the
     * <code>IMessageEditorTabFactory</code> interface, which the extension can
     * query to obtain details of the context menu invocation.
     * @return A list of custom menu items (which may include sub-menus,
     * checkbox menu items, etc.) that should be displayed. Extensions may
     * return
     * <code>null</code> from this method, to indicate that no menu items are
     * required.
     */
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
      // IRubyObject ret = null;
      if (r_obj != null && r_obj.respondsTo(MENUFAC_METH))
        return (RubyArray)r_obj.callMethod(ctx(r_obj), MENUFAC_METH, to_ruby(rt(r_obj), invocation));
      return null;
    }

    /**
     * This method is invoked whenever a change occurs to Burp's suite-wide
     * target scope.
     */
    public void scopeChanged() {
      if (r_obj != null && r_obj.respondsTo(SCOPE_METH))
        r_obj.callMethod(ctx(r_obj), SCOPE_METH);
    }
}

