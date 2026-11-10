package com.junzhou.mapper;
import com.junzhou.report.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface ReportMapper {
 List<ReportRow> daily(ReportFilter f);
 List<ReportRow> sales(ReportFilter f);
 List<ReportRow> users(ReportFilter f);
 List<ReportRow> itemDetails(ReportFilter f);
 List<ReportRow> ledger(ReportFilter f);
 List<ReportRow> overview(ReportFilter f);
 List<ReportRow> menu(@Param("f") ReportFilter f,@Param("kind") String kind);
}
