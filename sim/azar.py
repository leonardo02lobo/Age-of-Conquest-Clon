"""Generación de variables aleatorias — §3.5 del documento del Parcial II.

Define F₇ (`Aleatorio()`), la única fuente de aleatoriedad del modelo. Cada
resolución de combate consume exactamente dos números: U_a y U_d.

No se usa `random` de la biblioteca estándar: el documento especifica un
generador congruencial lineal concreto con R_k = X_{k+1}/m (ec. 3.26), y
`random.random()` no cumple esa definición. Implementarlo explícitamente
permite además verificar la afirmación de "dos números por combate".
"""

from __future__ import annotations

import math
from typing import Iterable, Iterator


class GeneradorLcg:
    """Generador congruencial lineal de 48 bits — ec. (3.26).

        X_{k+1} = (a·X_k + c) mod m,   R_k = X_{k+1}/m
        a = 25 214 903 917,  c = 11,  m = 2⁴⁸

    Estos valores satisfacen las condiciones de Hull–Dobell, por lo que el
    generador tiene periodo completo m = 2⁴⁸ ≈ 2.81·10¹⁴ — ocho órdenes de
    magnitud por encima del consumo de un experimento de 10⁴ réplicas.
    """

    A = 25_214_903_917
    C = 11
    M = 1 << 48

    def __init__(self, semilla: int):
        self.semilla_inicial = semilla
        self.x = semilla % self.M
        self.consumidos = 0

    def uniforme(self) -> float:
        """Siguiente R ~ Uniforme(0,1)."""
        self.x = (self.A * self.x + self.C) % self.M
        self.consumidos += 1
        return self.x / self.M

    def reiniciar(self) -> None:
        self.x = self.semilla_inicial % self.M
        self.consumidos = 0


class GeneradorFijo:
    """Generador que devuelve una secuencia predeterminada de uniformes.

    Es el mecanismo que hace verificable la traza de escritorio de §4.7, donde
    el documento fija R₁ = 0.7314 y R₂ = 0.2891. Al agotarse la secuencia
    lanza una excepción, de modo que un consumo inesperado de aleatorios se
    detecta en vez de pasar desapercibido.
    """

    def __init__(self, valores: Iterable[float]):
        self._valores: Iterator[float] = iter(list(valores))
        self.consumidos = 0

    def uniforme(self) -> float:
        try:
            v = next(self._valores)
        except StopIteration:  # pragma: no cover - señal de error de prueba
            raise RuntimeError(
                "GeneradorFijo agotado: el modelo consumió más aleatorios de los previstos"
            ) from None
        self.consumidos += 1
        return v


def triangular_inversa(r: float, a: float = 0.8, c: float = 1.0, b: float = 1.2) -> float:
    """U = F⁻¹(R) para U ~ Triangular(a, c, b) — ec. (3.25).

        R < 0.5:  U = a + sqrt(R·(b−a)·(c−a))
        R ≥ 0.5:  U = b − sqrt((1−R)·(b−a)·(b−c))

    Con (0.8, 1.0, 1.2) la distribución es simétrica, E[U] = 1 —de modo que el
    factor aleatorio no sesga la potencia media de ningún bando— y σ = 0.0816.
    La expresión es continua en R = 0.5: ambas ramas dan exactamente c.
    """
    if not 0.0 <= r <= 1.0:
        raise ValueError(f"R debe estar en [0,1], recibido {r}")
    if r < 0.5:
        return a + math.sqrt(r * (b - a) * (c - a))
    return b - math.sqrt((1.0 - r) * (b - a) * (b - c))
