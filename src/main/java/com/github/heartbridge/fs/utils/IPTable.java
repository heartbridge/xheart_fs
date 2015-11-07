package com.github.heartbridge.fs.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ip table ,contains the allow rule and the deny rule, used to check the remote ip address is allowed.the flow of ip table is :
 * <ul>
 * <li>1. first check the deny rule, if matched, will determine it blocked directly. otherwise is deny rule is not set,go step 2</li>
 * <li>2. if not matched in deny rule, then check the allow rule.
 * <ul>
 * <li>if the allow rule is not set, go step 2</li>
 * <li>allow rule is set, if matched, the ip not block, OR will blocked </li>
 * </ul>
 * </li>
 * <li>3. if the allow rule and the deny rule both not set, no ip will blocked</li>
 * </ul>
 * @author GavinCook
 * @since 1.0.0
 **/
public class IPTable {

    private Pattern allowPattern ,denyPattern ;

    /**
     * set the allow rule, use the regex syntax, if set multiple times, only the last affect
     * @param allowRegex the allow regex
     * @return ip table instance
     */
    public IPTable allow(String allowRegex){
        this.allowPattern = Pattern.compile(allowRegex);
        return this;
    }

    /**
     * set the deny rule, use the regex syntax, if set multiple times, only the last affect
     * @param denyRegex the deny regex
     * @return ip table instance
     */
    public IPTable deny(String denyRegex){
        this.denyPattern = Pattern.compile(denyRegex);
        return this;
    }

    /**
     * check if the ip address is blocked by current ip table
     * @param ip the request ip
     * @return <code>true</code> if blocked
     */
    public boolean isBlocked(String ip){
        Matcher matcher;
        if(denyPattern != null) {
            matcher = denyPattern.matcher(ip);
            if (matcher.find()) {
                return true;
            }
        }

        if(allowPattern != null) {
            matcher = allowPattern.matcher(ip);
            return !matcher.find();
        }

        return false;
    }

}
