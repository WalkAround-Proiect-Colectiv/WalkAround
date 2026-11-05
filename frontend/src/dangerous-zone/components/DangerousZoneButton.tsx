import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { AlertTriangle } from 'lucide-react-native';

export type DangerousZoneButtonProps = {
    onPress: () => void;
};

export default function DangerousZoneButton({ onPress }: DangerousZoneButtonProps) {
    return (
        <Pressable
            accessibilityRole="button"
            accessibilityLabel="Report dangerous zone"
            onPress={onPress}
            style={styles.fab}
        >
            <AlertTriangle size={24} color="#ffffff" />
        </Pressable>
    );
}

const styles = StyleSheet.create({
    fab: {
        position: 'absolute',
        right: 20,
        bottom: 80,
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: '#d93838',
        paddingVertical: 12,
        paddingHorizontal: 16,
        borderRadius: 30,
        gap: 8,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.25,
        shadowRadius: 3.84,
        elevation: 5,
        zIndex: 100,
    },
    fabText: {
        color: '#ffffff',
        fontWeight: '700',
        fontSize: 14,
    },
});