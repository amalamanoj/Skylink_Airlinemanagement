import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AirlineManagementSystem.java
 * Single-file Swing application.
 *
 * Admin:
 *  - Login (admin/admin123)
 *  - Add / Edit / Delete flights
 *  - View all bookings & registered users
 *
 * User:
 *  - Register (username, password, passport) — passport validated by regex ^[A-Z][0-9]{7}$
 *  - Login
 *  - View flights (search/filter)
 *  - Book seats (multi-seat), cancel own bookings, view booking history
 *
 * In-memory data (no DB). Easy to extend.
 */
public class AirlineManagementSystem extends JFrame {
    // ---------------- Models ----------------
    static class Flight {
        String flightNo;
        String source;
        String destination;
        String date; // YYYY-MM-DD
        String time; // HH:mm
        double price;
        int totalSeats;
        int availableSeats;

        Flight(String flightNo, String source, String dest, String date, String time, double price, int seats) {
            this.flightNo = flightNo;
            this.source = source;
            this.destination = dest;
            this.date = date;
            this.time = time;
            this.price = price;
            this.totalSeats = seats;
            this.availableSeats = seats;
        }
    }

    static class User {
        String username;
        String password;
        String passport;
        List<Booking> myBookings = new ArrayList<>();

        User(String username, String password, String passport) {
            this.username = username;
            this.password = password;
            this.passport = passport;
        }
    }

    static class Booking {
        String bookingId;
        User user;
        Flight flight;
        int seatsBooked;
        double totalPrice;

        Booking(String bookingId, User user, Flight flight, int seatsBooked) {
            this.bookingId = bookingId;
            this.user = user;
            this.flight = flight;
            this.seatsBooked = seatsBooked;
            this.totalPrice = seatsBooked * flight.price;
        }
    }

    // ---------------- Data stores ----------------
    private final List<Flight> flights = new ArrayList<>();
    private final List<User> users = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();

    private User currentUser = null;
    private boolean adminLoggedIn = false;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);

    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Z][0-9]{7}$");
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin123";

    // Admin table models (so we can refresh easily)
    private DefaultTableModel adminFlightsModel;
    private DefaultTableModel adminBookingsModel;
    private DefaultTableModel usersModel;

    // User table models
    private DefaultTableModel userFlightsModel;
    private DefaultTableModel userBookingsModel;

    public AirlineManagementSystem() {
        setTitle("✈ SkyLink - Airline Management System");
        setSize(1100, 720);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); } catch (Exception ignored) {}

        seedSampleData(); // sample data to test immediately

        mainPanel.add(createLoginPanel(), "Login");
        mainPanel.add(createAdminPanel(), "Admin");
        mainPanel.add(createUserPanel(), "User");

        add(mainPanel);
        cardLayout.show(mainPanel, "Login");
        setVisible(true);
    }

    // ---------------- Login / Register ----------------
    private JPanel createLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(22, 101, 178));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);

        JLabel title = new JLabel("SkyLink Airlines");
        title.setFont(new Font("Segoe UI", Font.BOLD, 40));
        title.setForeground(Color.WHITE);

        JTextField usernameFld = new JTextField(16);
        JPasswordField passwordFld = new JPasswordField(16);
        JTextField passportFld = new JTextField(12);
        JComboBox<String> roleCombo = new JComboBox<>(new String[]{"User", "Admin"});
        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register (User)");

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; p.add(title, gbc);
        gbc.gridwidth = 1;
        gbc.gridy++; gbc.gridx = 0; p.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; p.add(usernameFld, gbc);
        gbc.gridy++; gbc.gridx = 0; p.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; p.add(passwordFld, gbc);
        gbc.gridy++; gbc.gridx = 0; p.add(new JLabel("Passport (User only):"), gbc);
        gbc.gridx = 1; p.add(passportFld, gbc);
        gbc.gridy++; gbc.gridx = 0; p.add(new JLabel("Role:"), gbc);
        gbc.gridx = 1; p.add(roleCombo, gbc);
        gbc.gridy++; gbc.gridx = 0; p.add(loginBtn, gbc);
        gbc.gridx = 1; p.add(registerBtn, gbc);

        // Login action
        loginBtn.addActionListener(e -> {
            String user = usernameFld.getText().trim();
            String pass = new String(passwordFld.getPassword());

            if ("Admin".equals(roleCombo.getSelectedItem())) {
                if (user.equals(ADMIN_USER) && pass.equals(ADMIN_PASS)) {
                    adminLoggedIn = true;
                    currentUser = null;
                    refreshAllAdminTables();
                    cardLayout.show(mainPanel, "Admin");
                    JOptionPane.showMessageDialog(this, "Admin logged in.");
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid admin credentials.", "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                Optional<User> maybe = users.stream().filter(u -> u.username.equals(user) && u.password.equals(pass)).findFirst();
                if (maybe.isPresent()) {
                    currentUser = maybe.get();
                    adminLoggedIn = false;
                    refreshUserFlightsTable();
                    refreshUserBookingsTable();
                    cardLayout.show(mainPanel, "User");
                    JOptionPane.showMessageDialog(this, "Welcome, " + currentUser.username + "!");
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid user credentials or not registered.", "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Register action
        registerBtn.addActionListener(e -> {
            String user = usernameFld.getText().trim();
            String pass = new String(passwordFld.getPassword());
            String passport = passportFld.getText().trim();

            if (user.isEmpty() || pass.isEmpty() || passport.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields required for registration.", "Registration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!PASSPORT_PATTERN.matcher(passport).matches()) {
                JOptionPane.showMessageDialog(this, "Invalid passport format! Must be 1 uppercase letter followed by 7 digits (e.g. A1234567).", "Passport Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            boolean exists = users.stream().anyMatch(u -> u.username.equals(user));
            if (exists) {
                JOptionPane.showMessageDialog(this, "Username already exists.", "Registration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            User newUser = new User(user, pass, passport);
            users.add(newUser);
            currentUser = newUser;
            adminLoggedIn = false;
            JOptionPane.showMessageDialog(this, "Registered and logged in as " + user + ".");
            refreshUserFlightsTable();
            refreshUserBookingsTable();
            cardLayout.show(mainPanel, "User");
        });

        return p;
    }

    // ---------------- Admin Panel ----------------
    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout(8,8));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JLabel header = new JLabel("Admin Control Panel", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 28));
        panel.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();

        // Flights tab
        adminFlightsModel = new DefaultTableModel(new Object[]{"FlightNo", "Source", "Destination", "Date", "Time", "Price", "Available"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable flightsTable = new JTable(adminFlightsModel);
        JScrollPane flightsScroll = new JScrollPane(flightsTable);

        JPanel flightForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JTextField fNo = new JTextField(6), fSrc = new JTextField(8), fDest = new JTextField(8), fDate = new JTextField(8), fTime = new JTextField(6), fPrice = new JTextField(6), fSeats = new JTextField(4);
        JButton addFlight = new JButton("Add Flight"), loadFlight = new JButton("Load Selected"), updateFlight = new JButton("Update"), deleteFlight = new JButton("Delete"), searchFlight = new JButton("Search FlightNo");

        flightForm.add(new JLabel("No:")); flightForm.add(fNo);
        flightForm.add(new JLabel("Source:")); flightForm.add(fSrc);
        flightForm.add(new JLabel("Dest:")); flightForm.add(fDest);
        flightForm.add(new JLabel("Date:")); flightForm.add(fDate);
        flightForm.add(new JLabel("Time:")); flightForm.add(fTime);
        flightForm.add(new JLabel("Price:")); flightForm.add(fPrice);
        flightForm.add(new JLabel("Seats:")); flightForm.add(fSeats);
        flightForm.add(addFlight); flightForm.add(loadFlight); flightForm.add(updateFlight); flightForm.add(deleteFlight); flightForm.add(searchFlight);

        JPanel flightsPanel = new JPanel(new BorderLayout());
        flightsPanel.add(flightsScroll, BorderLayout.CENTER);
        flightsPanel.add(flightForm, BorderLayout.SOUTH);

        tabs.addTab("Manage Flights", flightsPanel);

        // Bookings tab
        adminBookingsModel = new DefaultTableModel(new Object[]{"BookingID", "User", "Passport", "FlightNo", "Destination", "Date", "Seats"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable bookingsTable = new JTable(adminBookingsModel);
        JScrollPane bookingsScroll = new JScrollPane(bookingsTable);
        JPanel bookingsPanel = new JPanel(new BorderLayout());
        bookingsPanel.add(bookingsScroll, BorderLayout.CENTER);
        JButton refreshBookings = new JButton("Refresh Bookings");
        bookingsPanel.add(refreshBookings, BorderLayout.SOUTH);
        tabs.addTab("All Bookings", bookingsPanel);

        // Users tab
        usersModel = new DefaultTableModel(new Object[]{"Username", "Passport", "BookingsCount"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable usersTable = new JTable(usersModel);
        JScrollPane usersScroll = new JScrollPane(usersTable);
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.add(usersScroll, BorderLayout.CENTER);
        JButton refreshUsers = new JButton("Refresh Users");
        usersPanel.add(refreshUsers, BorderLayout.SOUTH);
        tabs.addTab("Registered Users", usersPanel);

        panel.add(tabs, BorderLayout.CENTER);

        // Admin controls actions
        addFlight.addActionListener(e -> {
            try {
                String no = fNo.getText().trim();
                String src = fSrc.getText().trim();
                String dest = fDest.getText().trim();
                String date = fDate.getText().trim();
                String time = fTime.getText().trim();
                double price = Double.parseDouble(fPrice.getText().trim());
                int seats = Integer.parseInt(fSeats.getText().trim());

                if (no.isEmpty() || src.isEmpty() || dest.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Flight number, source and destination required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                boolean exists = flights.stream().anyMatch(f -> f.flightNo.equalsIgnoreCase(no));
                if (exists) {
                    JOptionPane.showMessageDialog(this, "Flight number already exists.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                Flight f = new Flight(no, src, dest, date, time, price, seats);
                flights.add(f);
                refreshAllAdminTables();
                clearTextFields(fNo, fSrc, fDest, fDate, fTime, fPrice, fSeats);
                JOptionPane.showMessageDialog(this, "Flight added.");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Price and seats must be numeric.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        loadFlight.addActionListener(e -> {
            int row = flightsTable.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(this, "Select a flight to load."); return; }
            Flight f = flights.get(row);
            fNo.setText(f.flightNo); fSrc.setText(f.source); fDest.setText(f.destination);
            fDate.setText(f.date); fTime.setText(f.time); fPrice.setText(String.valueOf(f.price)); fSeats.setText(String.valueOf(f.totalSeats));
        });

        updateFlight.addActionListener(e -> {
            int row = flightsTable.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(this, "Select a flight to update (Load Selected)."); return; }
            try {
                String no = fNo.getText().trim();
                String src = fSrc.getText().trim();
                String dest = fDest.getText().trim();
                String date = fDate.getText().trim();
                String time = fTime.getText().trim();
                double price = Double.parseDouble(fPrice.getText().trim());
                int seats = Integer.parseInt(fSeats.getText().trim());

                Flight existing = flights.get(row);
                int booked = existing.totalSeats - existing.availableSeats;
                existing.flightNo = no;
                existing.source = src;
                existing.destination = dest;
                existing.date = date;
                existing.time = time;
                existing.price = price;
                existing.totalSeats = seats;
                existing.availableSeats = Math.max(0, seats - booked);

                refreshAllAdminTables();
                JOptionPane.showMessageDialog(this, "Flight updated.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid update values.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        deleteFlight.addActionListener(e -> {
            int row = flightsTable.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(this, "Select a flight to delete."); return; }
            Flight toRemove = flights.get(row);
            // remove associated bookings
            List<Booking> toDel = new ArrayList<>();
            for (Booking b : bookings) if (b.flight == toRemove) toDel.add(b);
            for (Booking b : toDel) {
                b.user.myBookings.remove(b);
                bookings.remove(b);
            }
            flights.remove(toRemove);
            refreshAllAdminTables();
            JOptionPane.showMessageDialog(this, "Flight deleted; associated bookings removed.");
        });

        searchFlight.addActionListener(e -> {
            String no = JOptionPane.showInputDialog(this, "Enter flight number:");
            if (no == null || no.trim().isEmpty()) return;
            for (int i = 0; i < flights.size(); i++) {
                if (flights.get(i).flightNo.equalsIgnoreCase(no.trim())) {
                    flightsTable.setRowSelectionInterval(i, i);
                    flightsTable.scrollRectToVisible(flightsTable.getCellRect(i,0,true));
                    return;
                }
            }
            JOptionPane.showMessageDialog(this, "Flight not found.");
        });

        refreshBookings.addActionListener(e -> refreshAllAdminTables());
        refreshUsers.addActionListener(e -> refreshUsersTable());

        // logout
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> {
            adminLoggedIn = false;
            cardLayout.show(mainPanel, "Login");
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(logout);
        panel.add(bottom, BorderLayout.SOUTH);

        refreshAllAdminTables();
        return panel;
    }

    // ---------------- User Panel ----------------
    private JPanel createUserPanel() {
        JPanel panel = new JPanel(new BorderLayout(8,8));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JLabel header = new JLabel("Passenger Portal", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 28));
        panel.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();

        // Flights table for users
        userFlightsModel = new DefaultTableModel(new Object[]{"#", "FlightNo", "Source", "Destination", "Date", "Time", "Price", "Available"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable flightsTbl = new JTable(userFlightsModel);
        JScrollPane flightsScroll = new JScrollPane(flightsTbl);

        JPanel searchBookPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton refreshFlights = new JButton("Refresh Flights");
        JTextField searchDestFld = new JTextField(12);
        JButton searchDestBtn = new JButton("Search Destination");
        JLabel seatLbl = new JLabel("Seats:");
        SpinnerModel seatModel = new SpinnerNumberModel(1, 1, 10, 1);
        JSpinner seatSpinner = new JSpinner(seatModel);
        JButton bookBtn = new JButton("Book Selected");

        searchBookPanel.add(refreshFlights);
        searchBookPanel.add(new JLabel("Destination:")); searchBookPanel.add(searchDestFld); searchBookPanel.add(searchDestBtn);
        searchBookPanel.add(seatLbl); searchBookPanel.add(seatSpinner); searchBookPanel.add(bookBtn);

        JPanel flightsTab = new JPanel(new BorderLayout());
        flightsTab.add(flightsScroll, BorderLayout.CENTER);
        flightsTab.add(searchBookPanel, BorderLayout.SOUTH);
        tabs.addTab("Search & Book Flights", flightsTab);

        // My bookings tab
        userBookingsModel = new DefaultTableModel(new Object[]{"BookingID", "FlightNo", "Dest", "Date", "Seats", "Total"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable myBookingsTbl = new JTable(userBookingsModel);
        JScrollPane myBookingsScroll = new JScrollPane(myBookingsTbl);
        JPanel myTab = new JPanel(new BorderLayout());
        myTab.add(myBookingsScroll, BorderLayout.CENTER);
        JButton refreshMyBookings = new JButton("Refresh My Bookings");
        JButton cancelBooking = new JButton("Cancel Selected Booking");
        JPanel myBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        myBtns.add(refreshMyBookings); myBtns.add(cancelBooking);
        myTab.add(myBtns, BorderLayout.SOU
