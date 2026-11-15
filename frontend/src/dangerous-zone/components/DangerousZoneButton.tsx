import React, { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { AlertTriangle } from 'lucide-react-native';

export type DangerousZoneButtonProps = {
    onPress: () => void;
};

export default function DangerousZoneButton({ onPress }: DangerousZoneButtonProps) {
    const [hovered, setHovered] = useState(false);

    return (
        <Pressable
            accessibilityRole="button"
            accessibilityLabel="Report dangerous zone"
            onHoverIn={() => setHovered(true)}
            onHoverOut={() => setHovered(false)}
            onPress={onPress}
            style={({ pressed }) => [styles.tool, pressed && styles.dim]}
        >
            <AlertTriangle size={21} color="#d93838" />
            {hovered && <Text style={styles.tooltip}>Report Danger</Text>}
        </Pressable>
    );
}

const styles = StyleSheet.create({
    tool: {
        width: 48,
        height: 48,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: 6,
        borderWidth: 1,
        borderColor: '#dce2e9',
        backgroundColor: '#ffffff',
        marginTop: 8,
    },
    dim: {
        opacity: 0.55,
    },
    tooltip: {
        position: 'absolute',
        right: 52,
        backgroundColor: '#ffffff',
        color: '#363d47',
        padding: 8,
        borderRadius: 4,
        width: 110,
        fontSize: 12,
        borderWidth: 1,
        borderColor: '#dce2e9',
        textAlign: 'center',
    },
});