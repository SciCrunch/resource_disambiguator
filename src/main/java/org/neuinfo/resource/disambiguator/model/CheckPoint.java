package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;
import java.util.Calendar;

/**
 * Created by bozyurt on 3/12/14.
 */

@Entity
@Table(name = "rd_checkpoint")
public class CheckPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "table_name", length = 50, nullable = false)
    private String tableName;

    @Column(name = "pk_value", nullable = false)
    private Long pkValue;

    @Column(name = "op_type", length = 50, nullable = false)
    /**
     *  such as publisher_search, reg_val_check
     */
    private String opType;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "mod_time", nullable = false)
    private Calendar modTime = Calendar.getInstance();

    @Column(name = "batch_id", length = 10, nullable = false)
    private String batchId;

    public static final String STATUS_START = "start";
    public static final String STATUS_END = "end";

    public long getId() {
        return id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Long getPkValue() {
        return pkValue;
    }

    public void setPkValue(Long pkValue) {
        this.pkValue = pkValue;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Calendar getModTime() {
        return modTime;
    }

    public void setModTime(Calendar modTime) {
        this.modTime = modTime;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
}
