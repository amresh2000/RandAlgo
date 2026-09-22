package com.penguinsecure.basis.core.command;

/** Urgent hedge and unwind traffic always drains before normal initiation traffic. */
public enum OrderUrgency {
    NORMAL,
    URGENT
}
