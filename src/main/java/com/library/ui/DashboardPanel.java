package com.library.ui;

import com.library.dao.BookDAO;
import javax.swing.*;
import java.awt.*;

public class DashboardPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();
    private BarChartPanel barChartPanel;

    public DashboardPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 顶部标题和刷新按钮
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("图书馆数据概览");
        title.setFont(new Font("微软雅黑", Font.BOLD, 24));
        JButton btnRefresh = new JButton("🔄 刷新数据");

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(btnRefresh, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // 创建柱形图面板
        barChartPanel = new BarChartPanel();
        add(barChartPanel, BorderLayout.CENTER);

        // 监听器
        btnRefresh.addActionListener(e -> loadData());

        // 初始加载
        loadData();
    }

    /**
     * ★★★ 加载并刷新数据（修复：使用正确的状态值）
     */
    private void loadData() {
        new Thread(() -> {
            try {
                // ★ 使用数据库中的实际状态值
                int total = bookDAO.getBookCountByStatus(null);         // 总数
                int available = bookDAO.getBookCountByStatus("available"); // 可借阅
                int borrowed = bookDAO.getBookCountByStatus("borrowed");   // 已借出
                int lost = bookDAO.getBookCountByStatus("lost");           // 遗失

                SwingUtilities.invokeLater(() -> {
                    barChartPanel.setData(total, available, borrowed, lost);
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            DashboardPanel.this,
                            "加载数据失败: " + e.getMessage(),
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }).start();
    }

    /**
     * 自定义柱形图面板
     */
    class BarChartPanel extends JPanel {
        private int total = 0;
        private int available = 0;
        private int borrowed = 0;
        private int lost = 0;

        private final String[] labels = {"总藏书量", "在馆可借", "当前借出", "遗失图书"};
        private final Color[] colors = {
                new Color(66, 139, 202),   // 蓝色
                new Color(92, 184, 92),    // 绿色
                new Color(240, 173, 78),   // 橙色
                new Color(217, 83, 79)     // 红色
        };

        public BarChartPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(800, 500)); // ★ 设置最小尺寸
        }

        public void setData(int total, int available, int borrowed, int lost) {
            this.total = total;
            this.available = available;
            this.borrowed = borrowed;
            this.lost = lost;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int[] values = {total, available, borrowed, lost};
            int maxValue = Math.max(1, getMaxValue(values)); // 避免除以0

            int width = getWidth();
            int height = getHeight();

            // ★ 调整布局参数
            int topMargin = 80;        // 顶部留白（给标题）
            int bottomMargin = 80;     // 底部留白（给标签）
            int leftMargin = 80;       // 左侧留白（给Y轴刻度）
            int rightMargin = 60;      // 右侧留白

            int chartWidth = width - leftMargin - rightMargin;
            int chartHeight = height - topMargin - bottomMargin;

            int barWidth = Math.min(80, chartWidth / 6); // 柱子宽度，最大80
            int maxBarHeight = chartHeight;

            // ★ 绘制图表标题（居中，距离顶部更远）
            g2d.setFont(new Font("微软雅黑", Font.BOLD, 18));
            g2d.setColor(new Color(50, 50, 50));
            String chartTitle = "图书状态统计";
            int titleWidth = g2d.getFontMetrics().stringWidth(chartTitle);
            g2d.drawString(chartTitle, (width - titleWidth) / 2, 40);

            // ★★★ 绘制柱形图
            for (int i = 0; i < values.length; i++) {
                int barHeight = maxValue > 0 ? (int) ((double) values[i] / maxValue * maxBarHeight) : 0;

                // ★ 计算柱子的X坐标（均匀分布）
                int x = leftMargin + (i * chartWidth / 4) + (chartWidth / 8) - (barWidth / 2);
                int y = topMargin + maxBarHeight - barHeight;

                // 绘制柱子（带圆角）
                g2d.setColor(colors[i]);
                g2d.fillRoundRect(x, y, barWidth, barHeight, 10, 10);

                // 绘制柱子边框
                g2d.setColor(colors[i].darker());
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(x, y, barWidth, barHeight, 10, 10);

                // ★ 绘制数值（柱子上方）
                g2d.setFont(new Font("Arial", Font.BOLD, 20));
                g2d.setColor(new Color(50, 50, 50));
                String valueStr = String.valueOf(values[i]);
                int strWidth = g2d.getFontMetrics().stringWidth(valueStr);
                g2d.drawString(valueStr, x + (barWidth - strWidth) / 2, y - 15);

                // ★ 绘制标签（柱子下方，距离更远）
                g2d.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                g2d.setColor(new Color(80, 80, 80));
                int labelWidth = g2d.getFontMetrics().stringWidth(labels[i]);
                g2d.drawString(labels[i], x + (barWidth - labelWidth) / 2, height - bottomMargin + 40);
            }

            // ★ 绘制底部基线
            g2d.setColor(new Color(200, 200, 200));
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(leftMargin, topMargin + maxBarHeight,
                    width - rightMargin, topMargin + maxBarHeight);

            // ★ 绘制Y轴
            g2d.drawLine(leftMargin, topMargin, leftMargin, topMargin + maxBarHeight);

            // ★ 绘制Y轴刻度线
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));
            g2d.setColor(new Color(150, 150, 150));
            for (int i = 0; i <= 5; i++) {
                int scaleValue = maxValue * i / 5;
                int scaleY = topMargin + maxBarHeight - (maxBarHeight * i / 5);

                // 刻度线
                g2d.drawLine(leftMargin - 5, scaleY, leftMargin, scaleY);

                // 刻度值
                String scaleStr = String.valueOf(scaleValue);
                int scaleWidth = g2d.getFontMetrics().stringWidth(scaleStr);
                g2d.drawString(scaleStr, leftMargin - scaleWidth - 10, scaleY + 5);
            }

            // ★★★ 如果数据为空，显示提示信息
            if (total == 0) {
                g2d.setFont(new Font("微软雅黑", Font.PLAIN, 16));
                g2d.setColor(new Color(150, 150, 150));
                String emptyMsg = "暂无数据，请添加图书";
                int msgWidth = g2d.getFontMetrics().stringWidth(emptyMsg);
                g2d.drawString(emptyMsg, (width - msgWidth) / 2, height / 2);
            }
        }

        private int getMaxValue(int[] values) {
            int max = 0;
            for (int value : values) {
                if (value > max) max = value;
            }
            return max;
        }
    }
}
