package com.library.ui;

import com.library.dao.BookDAO;
import javax.swing.*;
import java.awt.*;

public class DashboardPanel extends JPanel {
    private BookDAO bookDAO = new BookDAO();

    // 显示数字的标签
    private JLabel lblTotal;
    private JLabel lblAvailable;
    private JLabel lblBorrowed;
    private JLabel lblLost; // ★ 新增：遗失图书标签

    public DashboardPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 顶部标题和刷新按钮
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("图书馆数据概览");
        title.setFont(new Font("微软雅黑", Font.BOLD, 24));
        JButton btnRefresh = new JButton("刷新数据");

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(btnRefresh, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // 数据卡片区域 (网格布局，2行2列，间距20)
        JPanel cardsPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        cardsPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        // 初始化标签
        lblTotal = new JLabel("0", JLabel.CENTER);
        lblAvailable = new JLabel("0", JLabel.CENTER);
        lblBorrowed = new JLabel("0", JLabel.CENTER);
        lblLost = new JLabel("0", JLabel.CENTER);

        // 添加四个卡片
        // 1. 总藏书量 (蓝色)
        cardsPanel.add(createCard("总藏书量", lblTotal, new Color(66, 139, 202)));

        // 2. 在馆可借 (绿色)
        cardsPanel.add(createCard("在馆可借", lblAvailable, new Color(92, 184, 92)));

        // 3. 当前借出 (橙色)
        cardsPanel.add(createCard("当前借出", lblBorrowed, new Color(240, 173, 78)));

        // 4. ★ 遗失图书 (红色)
        cardsPanel.add(createCard("遗失图书", lblLost, new Color(217, 83, 79)));

        add(cardsPanel, BorderLayout.CENTER);

        // 监听器
        btnRefresh.addActionListener(e -> loadData());

        // 初始加载
        loadData();
    }

    /**
     * 创建一个美观的数据卡片
     */
    private JPanel createCard(String titleText, JLabel valueLabel, Color bgColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(bgColor);
        card.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel(titleText, JLabel.CENTER);
        title.setFont(new Font("微软雅黑", Font.PLAIN, 18));
        title.setForeground(Color.WHITE);

        valueLabel.setFont(new Font("Arial", Font.BOLD, 48));
        valueLabel.setForeground(Color.WHITE);

        card.add(title, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    /**
     * 加载并刷新数据
     */
    private void loadData() {
        // 使用 SwingWorker 或新线程来避免卡顿 UI (简单起见这里直接调用)
        new Thread(() -> {
            // null 表示查总数
            int total = bookDAO.getBookCountByStatus(null);
            int available = bookDAO.getBookCountByStatus("可借阅");
            int borrowed = bookDAO.getBookCountByStatus("已借出"); // 注意兼容 "borrowed"
            // ★ 查询遗失数量
            int lost = bookDAO.getBookCountByStatus("遗失");

            // 如果您在数据库里混用了 "borrowed" 和 "已借出"，建议做加法：
            // int borrowed = bookDAO.getBookCountByStatus("已借出") + bookDAO.getBookCountByStatus("borrowed");

            SwingUtilities.invokeLater(() -> {
                lblTotal.setText(String.valueOf(total));
                lblAvailable.setText(String.valueOf(available));
                lblBorrowed.setText(String.valueOf(borrowed));
                lblLost.setText(String.valueOf(lost));
            });
        }).start();
    }
}