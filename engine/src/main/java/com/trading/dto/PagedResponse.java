package com.trading.dto;

import lombok.Data;

import java.util.List;

/**
 * 【職責】通用分頁回應包裝：{@code data} 放本頁資料，{@code meta} 放分頁中繼資訊。
 * 【技巧】泛型 {@code PagedResponse<T>}；巢狀 {@link PageMeta} + 靜態工廠 {@link PageMeta#of}。
 * 【概念】列表 API 統一形狀後，前端 DataTable／分頁元件可共用解析邏輯，不必每支 API 各寫一套。
 *
 * @param <T> 本頁元素型別（如 OrderResponse、TradeDetailResponse）
 */
@Data
public class PagedResponse<T> {

    /** 本頁資料列表。 */
    private List<T> data;
    /** 分頁中繼資訊。 */
    private PageMeta meta;

    /**
     * 【職責】描述當前頁在整體結果集中的位置（page／size／total／totalPages）。
     * 【技巧】靜態巢狀類 + {@link #of} 計算 totalPages。
     * 【概念】把「怎麼算總頁數」集中在一處，避免各 Controller 重複 ceil 公式。
     */
    @Data
    public static class PageMeta {
        /** 當前頁碼（從 0 起）。 */
        private int page;
        /** 每頁筆數上限。 */
        private int size;
        /** 符合條件的總筆數。 */
        private long total;
        /** 總頁數。 */
        private int totalPages;

        /**
         * 【職責】依頁碼、每頁大小與總筆數建立分頁中繼。
         * 【技巧】{@code size == 0} 時 totalPages 為 0，避免除以零。
         * 【概念】工廠方法讓呼叫端一行完成，不必手動設四個欄位。
         */
        public static PageMeta of(int page, int size, long total) {
            PageMeta meta = new PageMeta();
            meta.page = page;
            meta.size = size;
            meta.total = total;
            meta.totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
            return meta;
        }
    }
}
