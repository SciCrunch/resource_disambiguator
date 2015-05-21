package org.neuinfo.resource.disambiguator.util;

import org.apache.log4j.Logger;

import java.sql.*;
import java.util.Properties;

/**
 * Created by bozyurt on 1/31/14.
 */
public class DOI2PMIDLookupUtil {
    private Connection con;
    private static DOI2PMIDLookupUtil instance;
    static Logger log = Logger.getLogger(DOI2PMIDLookupUtil.class);

    private DOI2PMIDLookupUtil() throws Exception {
        Properties props = new Properties();
        props.put("user", "user");
        props.put("password", "");
        con = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/disco_crawler", props);
    }

    public synchronized static DOI2PMIDLookupUtil getInstance() throws Exception {
        if (instance == null) {
            instance = new DOI2PMIDLookupUtil();
        }
        return instance;
    }

    public String getPMID(String doi) {
        String pmid = null;
        PreparedStatement st = null;
        try {
            st = con.prepareStatement("select pmid from l2_nlx_82958_pubmed_detail where elocationid_doi = ?");
            st.setString(1, doi);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                pmid = rs.getString(1);
            }
            rs.close();
            return pmid;
        } catch (Exception x) {
            log.error(x.getMessage());
            return null;
        } finally {
            if (st != null) {
                try {
                    st.close();
                } catch (SQLException e) {
                    // ignore
                }
            }
        }
    }

    public synchronized void shutdown() {
        if (con != null) {
            try {
                con.close();
            } catch (Exception x) {
                // ignore
            }
        }
    }
}
